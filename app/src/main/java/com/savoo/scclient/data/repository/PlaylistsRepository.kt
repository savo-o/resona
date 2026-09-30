package com.savoo.scclient.data.repository

import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.local.LocalPlaylistDao
import com.savoo.scclient.data.model.LocalPlaylist
import com.savoo.scclient.data.model.LocalPlaylistSummary
import com.savoo.scclient.data.model.LocalPlaylistTrack
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.toLocalPlaylistTrack
import com.savoo.scclient.data.remote.BridgeResponse
import com.savoo.scclient.data.remote.SoundCloudApi
import com.savoo.scclient.data.remote.WebViewApiBridge
import com.savoo.scclient.debug.DebugLog
import com.savoo.scclient.data.model.isLocalPlaylistRouteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

class PlaylistRequestException(val code: Int, serverMessage: String?) :
    Exception(serverMessage ?: "HTTP $code")

class PlaylistFullException : Exception("Playlist track limit reached")

data class CreatedOnlinePlaylist(val id: Long, val title: String)

data class PlaylistAddProgress(
    val routeId: Long,
    val title: String,
    val total: Int,
    val done: Int = 0,
    val added: Int = 0,
    val finished: Boolean = false,
    val errorMessage: String? = null,
    val isFull: Boolean = false,
) {
    val isLocal: Boolean get() = isLocalPlaylistRouteId(routeId)
    val fraction: Float? get() = if (isLocal && total > 0) done.toFloat() / total else null
    val failed: Boolean get() = errorMessage != null || isFull
}

@Singleton
class PlaylistsRepository @Inject constructor(
    private val localPlaylistDao: LocalPlaylistDao,
    private val favoritesDao: FavoritesDao,
    private val api: SoundCloudApi,
    private val trackRepository: TrackRepository,
    private val webBridge: WebViewApiBridge,
    private val tokenStore: TokenStore,
) {
    val isLoggedIn = tokenStore.isLoggedIn

    private val bulkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bulkMutex = Mutex()
    private val bulkLock = Any()
    private var bulkJobs = emptyList<Job>()

    private val _ownOnline = MutableStateFlow<List<Playlist>>(emptyList())
    val ownOnlinePlaylists = _ownOnline.asStateFlow()

    init {
        bulkScope.launch {
            tokenStore.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    refreshOwnOnline()
                } else {
                    _ownOnline.value.filter { it.sharing == "private" }.forEach { favoritesDao.removePlaylist(it.id) }
                    _ownOnline.value = emptyList()
                    trackRepository.resetCurrentUser()
                }
            }
        }
    }

    suspend fun refreshOwnOnline() {
        if (!tokenStore.isLoggedIn.value) return
        runCatching { onlinePlaylists() }
            .onSuccess { _ownOnline.value = it }
            .onFailure { DebugLog.log(TAG, "refreshOwnOnline failed: ${it.message}") }
    }

    private val _addProgress = MutableStateFlow<PlaylistAddProgress?>(null)
    val addProgress = _addProgress.asStateFlow()

    fun enqueueAdd(routeId: Long, title: String, tracks: List<Track>) {
        val job = bulkScope.launch(start = CoroutineStart.LAZY) {
            bulkMutex.withLock { runBulkAdd(routeId, title, tracks.distinctBy { it.id }) }
        }
        synchronized(bulkLock) { bulkJobs = bulkJobs.filter { it.isActive } + job }
        job.start()
    }

    fun cancelBulkAdd() {
        synchronized(bulkLock) {
            bulkJobs.forEach { it.cancel() }
            bulkJobs = emptyList()
        }
        _addProgress.value = null
    }

    private suspend fun runBulkAdd(routeId: Long, title: String, tracks: List<Track>) {
        _addProgress.value = PlaylistAddProgress(routeId, title, total = tracks.size)
        try {
            if (isLocalPlaylistRouteId(routeId)) {
                tracks.chunked(BULK_CHUNK_SIZE).forEach { chunk ->
                    val added = addToLocal(-routeId, chunk)
                    _addProgress.update { it?.copy(done = it.done + chunk.size, added = it.added + added) }
                }
            } else {
                val added = addToOnline(routeId, tracks.map { it.id })
                _addProgress.update { it?.copy(done = tracks.size, added = added) }
            }
            _addProgress.update { it?.copy(finished = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlaylistFullException) {
            _addProgress.update { it?.copy(finished = true, isFull = true) }
        } catch (e: Exception) {
            _addProgress.update { it?.copy(finished = true, errorMessage = e.message ?: e.javaClass.simpleName) }
        }
        delay(BULK_DONE_VISIBLE_MS)
        _addProgress.value = null
    }

    suspend fun localTrackIds(localId: Long): List<Long> = localPlaylistDao.trackIds(localId)

    fun observeLocalPlaylists(): Flow<List<LocalPlaylistSummary>> = localPlaylistDao.observeSummaries()

    fun observeLocalPlaylist(localId: Long): Flow<LocalPlaylist?> = localPlaylistDao.observePlaylist(localId)

    fun observeLocalTracks(localId: Long): Flow<List<LocalPlaylistTrack>> = localPlaylistDao.observeTracks(localId)

    suspend fun createLocal(title: String, tracks: List<Track>): Long =
        localPlaylistDao.createWithTracks(
            LocalPlaylist(title = title.trim()),
            tracks.map { it.toLocalPlaylistTrack(playlistId = 0, position = 0) },
        )

    suspend fun addToLocal(localId: Long, tracks: List<Track>): Int =
        localPlaylistDao.appendTracks(localId, tracks.map { it.toLocalPlaylistTrack(localId, 0) })

    suspend fun removeFromLocal(localId: Long, trackIds: Collection<Long>) =
        localPlaylistDao.removeTracks(localId, trackIds.toList())

    suspend fun renameLocal(localId: Long, title: String) =
        localPlaylistDao.rename(localId, title.trim(), System.currentTimeMillis())

    suspend fun deleteLocal(localId: Long) = localPlaylistDao.deletePlaylist(localId)

    suspend fun currentUserId(): Long? = if (tokenStore.isLoggedIn.value) trackRepository.currentUserId() else null

    suspend fun onlinePlaylists(): List<Playlist> {
        val userId = currentUserId() ?: error("Not logged in")
        return api.getUserPlaylists(userId, limit = ONLINE_PAGE_SIZE).collection
    }

    suspend fun createOnline(title: String, isPrivate: Boolean, tracks: List<Track>): CreatedOnlinePlaylist {
        val trackIds = tracks.map { it.id }.distinct()
        if (trackIds.size > ONLINE_TRACK_LIMIT) throw PlaylistFullException()
        val body = JSONObject().put(
            "playlist",
            JSONObject()
                .put("title", title.trim())
                .put("sharing", if (isPrivate) "private" else "public")
                .put("tracks", JSONArray(trackIds)),
        )
        val response = webBridge.createPlaylist(body.toString())
        DebugLog.log(TAG, "createOnline(${trackIds.size} tracks) -> ${response.code}")
        response.throwIfFailed()
        val json = JSONObject(response.body.orEmpty())
        val id = json.getLong("id")
        refreshOwnOnline()
        return CreatedOnlinePlaylist(id, title.trim())
    }

    suspend fun addToOnline(playlistId: Long, trackIds: List<Long>): Int {
        val current = currentOnlineTrackIds(playlistId)
        val fresh = trackIds.distinct().filter { it !in current }
        if (fresh.isEmpty()) return 0
        val merged = current + fresh
        if (merged.size > ONLINE_TRACK_LIMIT) throw PlaylistFullException()
        putOnlineTracks(playlistId, merged)
        return fresh.size
    }

    suspend fun removeFromOnline(playlistId: Long, trackIds: Collection<Long>) {
        val remove = trackIds.toSet()
        val current = currentOnlineTrackIds(playlistId)
        val remaining = current.filter { it !in remove }
        if (remaining.size == current.size) return
        putOnlineTracks(playlistId, remaining)
    }

    suspend fun renameOnline(playlistId: Long, title: String) {
        val body = JSONObject().put("playlist", JSONObject().put("title", title.trim()))
        val response = webBridge.updatePlaylist(playlistId, body.toString())
        DebugLog.log(TAG, "renameOnline($playlistId) -> ${response.code}")
        response.throwIfFailed()
        favoritesDao.renamePlaylist(playlistId, title.trim())
        _ownOnline.update { list -> list.map { if (it.id == playlistId) it.copy(title = title.trim()) else it } }
    }

    suspend fun deleteOnline(playlistId: Long) {
        val response = webBridge.deletePlaylist(playlistId)
        DebugLog.log(TAG, "deleteOnline($playlistId) -> ${response.code}")
        if (response.code != 404) response.throwIfFailed()
        favoritesDao.removePlaylist(playlistId)
        _ownOnline.update { list -> list.filter { it.id != playlistId } }
    }

    private suspend fun currentOnlineTrackIds(playlistId: Long): List<Long> =
        api.getPlaylist(playlistId).tracks.orEmpty().map { it.id }

    private suspend fun putOnlineTracks(playlistId: Long, trackIds: List<Long>) {
        val body = JSONObject().put("playlist", JSONObject().put("tracks", JSONArray(trackIds)))
        val response = webBridge.updatePlaylist(playlistId, body.toString())
        DebugLog.log(TAG, "putOnlineTracks($playlistId, ${trackIds.size} tracks) -> ${response.code}")
        response.throwIfFailed()
        favoritesDao.updatePlaylistTrackCount(playlistId, trackIds.size)
        _ownOnline.update { list -> list.map { if (it.id == playlistId) it.copy(trackCount = trackIds.size) else it } }
    }

    private fun BridgeResponse.throwIfFailed() {
        if (!isSuccess) throw PlaylistRequestException(code, serverMessage(body))
    }

    private fun serverMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return body.take(200)
        val errors = json.optJSONArray("errors") ?: return json.optString("error").ifBlank { null }
        return (0 until errors.length()).mapNotNull { index ->
            when (val item = errors.opt(index)) {
                is JSONObject -> item.optString("error_message").ifBlank { item.optString("message") }.ifBlank { null }
                is String -> item
                else -> null
            }
        }.joinToString("; ").ifBlank { null }
    }

    companion object {
        const val ONLINE_TRACK_LIMIT = 500
        const val BULK_ADD_THRESHOLD = 40
        private const val BULK_CHUNK_SIZE = 50
        private const val BULK_DONE_VISIBLE_MS = 4000L
        private const val ONLINE_PAGE_SIZE = 200
        private const val TAG = "PlaylistsRepository"
    }
}
