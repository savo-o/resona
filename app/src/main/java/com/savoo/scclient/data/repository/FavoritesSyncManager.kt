package com.savoo.scclient.data.repository

import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.local.UnavailableTrackDao
import com.savoo.scclient.data.model.UnavailableReason
import com.savoo.scclient.data.model.UnavailableTrackEntity
import com.savoo.scclient.data.remote.BridgeResponse
import com.savoo.scclient.debug.DebugLog
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

enum class FavoritesSyncStop { SPAM_WARNING, CAPTCHA, REFUSED }

data class SpamWarning(val urn: String, val expiresAt: Long?)

data class FavoritesSyncState(
    val total: Int,
    val done: Int = 0,
    val failed: Int = 0,
    val unavailable: Int = 0,
    val currentTitle: String? = null,
    val finished: Boolean = false,
    val aborted: Boolean = false,
    val stop: FavoritesSyncStop? = null,
    val blockedUntil: Long? = null,
) {
    val fraction: Float get() = if (total == 0) 1f else done.toFloat() / total
    val synced: Int get() = done - failed - unavailable
}

@Singleton
class FavoritesSyncManager @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val unavailableTrackDao: UnavailableTrackDao,
    private val trackRepository: TrackRepository,
    private val tokenStore: TokenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var job: Job? = null

    private val _state = MutableStateFlow<FavoritesSyncState?>(null)
    val state = _state.asStateFlow()

    val localOnlyCount: Flow<Int> = favoritesDao.observeLocalOnlyCount()

    private val _spamWarning = MutableStateFlow<SpamWarning?>(null)
    val spamWarning = _spamWarning.asStateFlow()

    suspend fun acknowledgeSpamWarning(): Boolean {
        val warning = _spamWarning.value ?: return true
        val response = runCatching { trackRepository.ackSpamWarning(warning.urn) }.getOrNull()
        DebugLog.log(TAG, "ack ${warning.urn} -> ${response?.code} ${response?.body?.take(160)}")
        val ok = response?.isSuccess == true
        if (ok) _spamWarning.value = null
        return ok
    }

    fun start(): Boolean {
        synchronized(lock) {
            if (job?.isActive == true || !tokenStore.isLoggedIn.value) return false
            job = scope.launch { run() }
            return true
        }
    }

    fun cancel() {
        synchronized(lock) {
            job?.cancel()
            job = null
            _state.value = null
        }
    }

    private suspend fun run() {
        val pending = favoritesDao.getLocalOnlyTracks()
        if (pending.isEmpty()) return
        _state.value = FavoritesSyncState(total = pending.size)
        var failuresInRow = 0
        var stop: FavoritesSyncStop? = null
        var blockedUntil: Long? = null
        for (track in pending) {
            _state.update { it?.copy(currentTitle = track.title) }
            val response = try {
                trackRepository.likeTrackResponse(track.trackId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.log(TAG, "like ${track.trackId} threw: ${e.message}")
                BridgeResponse(0, null)
            }
            val code = response.code
            val body = response.body.orEmpty()
            val ok = response.isSuccess
            val spamWarning = code == 429 && "spam_warning" in body
            val captcha = code == 403 && "captcha-delivery" in body
            val gone = !ok && !captcha &&
                (code == 404 || code == 410 || (code == 403 && isGoneFromSoundCloud(track.trackId)))
            when {
                ok -> favoritesDao.updateTrackSource(track.trackId, "BOTH")
                gone -> unavailableTrackDao.markIfAbsent(
                    UnavailableTrackEntity(track.trackId, UnavailableReason.DELETED, System.currentTimeMillis())
                )
            }
            if (!ok) DebugLog.log(TAG, "like ${track.trackId} (${track.title}) -> HTTP $code ${body.take(160)}")
            if (spamWarning || captcha) {
                stop = if (spamWarning) FavoritesSyncStop.SPAM_WARNING else FavoritesSyncStop.CAPTCHA
                blockedUntil = Regex("\"expires_at\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                if (spamWarning) {
                    Regex("\"urn\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.let { urn ->
                        _spamWarning.value = SpamWarning(urn, blockedUntil)
                    }
                }
                break
            }
            failuresInRow = if (ok || gone) 0 else failuresInRow + 1
            _state.update {
                it?.copy(
                    done = it.done + 1,
                    failed = it.failed + if (ok || gone) 0 else 1,
                    unavailable = it.unavailable + if (gone) 1 else 0,
                )
            }
            if ((!ok && !gone && (code == 403 || code == 429)) || failuresInRow >= MAX_FAILURES_IN_ROW) {
                stop = FavoritesSyncStop.REFUSED
                break
            }
            delay(PAUSE_BETWEEN_LIKES_MS)
        }
        DebugLog.log(TAG, "sync finished: ${_state.value}, stop=$stop")
        _state.update {
            it?.copy(finished = true, aborted = stop != null, stop = stop, blockedUntil = blockedUntil, currentTitle = null)
        }
        delay(if (stop != null) STOPPED_VISIBLE_MS else DONE_VISIBLE_MS)
        _state.value = null
    }

    private suspend fun isGoneFromSoundCloud(trackId: Long): Boolean = try {
        trackRepository.getTrack(trackId)
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        e.code() == 404 || e.code() == 410
    } catch (e: Exception) {
        false
    }

    private companion object {
        const val TAG = "FavoritesSyncManager"
        const val MAX_FAILURES_IN_ROW = 3
        const val PAUSE_BETWEEN_LIKES_MS = 3000L
        const val DONE_VISIBLE_MS = 6000L
        const val STOPPED_VISIBLE_MS = 15000L
    }
}
