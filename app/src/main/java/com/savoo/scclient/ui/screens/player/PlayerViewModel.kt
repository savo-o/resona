package com.savoo.scclient.ui.screens.player

import com.savoo.scclient.data.repository.FavoritesImportManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import com.savoo.scclient.R
import com.savoo.scclient.data.local.ExcludedArtistDao
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.ExcludedMixArtist
import com.savoo.scclient.data.model.LyricsResult
import com.savoo.scclient.data.model.LyricsSource
import com.savoo.scclient.data.model.LyricsSync
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.TrackComment
import com.savoo.scclient.data.repository.FavoritesRepository
import com.savoo.scclient.data.repository.LyricsRepository
import com.savoo.scclient.data.repository.SeekBarStyle
import com.savoo.scclient.data.repository.TimedCommentsRate
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.UndoAction
import com.savoo.scclient.ui.components.UndoController
import com.savoo.scclient.ui.screens.home.HomeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val controller: PlayerController,
    private val favoritesDao: FavoritesDao,
    private val favoritesRepository: FavoritesRepository,
    private val offlineTrackManager: OfflineTrackManager,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: SettingsRepository,
    private val trackRepository: com.savoo.scclient.data.repository.TrackRepository,
    private val tokenStore: com.savoo.scclient.auth.TokenStore,
    private val excludedArtistDao: ExcludedArtistDao,
    val undoController: UndoController,
    private val favoritesImportManager: FavoritesImportManager,
) : ViewModel() {

    val isFavorite = controller.state.map { it.currentTrack?.id ?: 0L }
        .distinctUntilChanged()
        .flatMapLatest { id -> favoritesDao.isTrackFavorite(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isMixPlaying = controller.state.map { it.queueTag == HomeViewModel.MIX_QUEUE_TAG }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isOffline = controller.state.map { it.currentTrack?.id ?: 0L }
        .distinctUntilChanged()
        .flatMapLatest { id -> offlineTrackManager.isOfflineTrack(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val currentTrackId = controller.state.map { it.currentTrack?.id }.distinctUntilChanged()

    private val forcedLyrics = MutableStateFlow<Pair<Long, LyricsResult>?>(null)
    private val forcingLyrics = MutableStateFlow(false)

    val lyricsForcing = forcingLyrics.asStateFlow()

    fun forceLyrics(source: LyricsSource) {
        val track = controller.state.value.currentTrack ?: return
        viewModelScope.launch {
            forcingLyrics.value = true
            forcedLyrics.value = null
            val result = runCatching { lyricsRepository.forceLyrics(track, source) }.getOrNull()
            if (result != null) forcedLyrics.value = track.id to result
            forcingLyrics.value = false
        }
    }

    private val loadedLyrics = combine(
        currentTrackId,
        controller.state.map { it.currentTrack }
            .distinctUntilChanged { old, new -> old?.id == new?.id }
            .flatMapLatest { track ->
                flow {
                    emit(null)
                    if (track != null) emit(track.id to lyricsRepository.getLyrics(track))
                }
            },
    ) { trackId, loaded -> loaded?.takeIf { it.first == trackId }?.second }

    val lyrics = combine(currentTrackId, loadedLyrics, forcedLyrics) { trackId, loaded, forced ->
        forced?.takeIf { it.first == trackId }?.second ?: loaded
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val lyricsTrackDurationMs = controller.state
        .map { state -> state.currentTrack?.durationMs?.takeIf { it > 0L } ?: state.durationMs.takeIf { it > 0L } }
        .distinctUntilChanged()

    private val automaticLyricsSync = combine(currentTrackId, lyrics, lyricsTrackDurationMs) { trackId, result, durationMs ->
        trackId to LyricsSync.automatic(durationMs, (result as? LyricsResult.Synced)?.sourceDurationMs)
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null to LyricsSync())

    private val storedLyricsSync = currentTrackId
        .flatMapLatest { trackId -> trackId?.let { id -> lyricsRepository.observeSync(id).map { id to it } } ?: flowOf(null) }

    private val pendingLyricsSync = MutableStateFlow<Pair<Long, LyricsSync>?>(null)
    private val lyricsSyncWriteLock = Mutex()

    private val lyricsSync = combine(currentTrackId, storedLyricsSync, automaticLyricsSync, pendingLyricsSync) { trackId, stored, automatic, pending ->
        val auto = automatic.second.takeIf { automatic.first == trackId } ?: LyricsSync()
        val sync = pending?.takeIf { it.first == trackId }?.second
            ?: stored?.takeIf { it.first == trackId }?.second?.let { LyricsSync(it.offsetMs, it.driftMsPerMin) }
            ?: auto
        LyricsSyncState(sync = sync, automatic = auto)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricsSyncState())

    val lyricsSyncState = lyricsSync

    val timedCommentsEnabled = settingsRepository.settings.map { it.timedCommentsEnabled }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val commentsRefresh = MutableStateFlow(0)

    val canComment = tokenStore.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val timedCommentsRate = settingsRepository.settings.map { it.timedCommentsRate }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimedCommentsRate.OFTEN)

    private val removedCommentIds = MutableStateFlow<Set<Long>>(emptySet())

    val currentUserId = tokenStore.isLoggedIn
        .map { loggedIn -> if (loggedIn) runCatching { trackRepository.getMe().id }.getOrNull() else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun deleteComment(comment: TrackComment) {
        viewModelScope.launch {
            val removed = runCatching { trackRepository.deleteComment(comment.id) }.isSuccess
            if (removed) {
                removedCommentIds.value = removedCommentIds.value + comment.id
                optimisticComments.value = optimisticComments.value.filterNot { it.id == comment.id }
            }
        }
    }

    suspend fun postComment(body: String, timestampMs: Long): TrackComment? {
        val trackId = controller.state.value.currentTrack?.id ?: return null
        val text = body.trim()
        if (text.isEmpty()) return null
        val result = runCatching { trackRepository.postComment(trackId, text, timestampMs) }
        if (result.isFailure) return null
        val posted = result.getOrNull() ?: TrackComment(
            id = -System.currentTimeMillis(),
            body = text,
            timestampMs = timestampMs,
        )
        optimisticComments.value = optimisticComments.value + posted
        commentsRefresh.value++
        return posted
    }

    private val optimisticComments = MutableStateFlow<List<TrackComment>>(emptyList())
    private var loadedCommentsTrackId: Long? = null

    private val fetchedComments = combine(
        currentTrackId,
        timedCommentsEnabled,
        commentsRefresh,
    ) { trackId, enabled, refreshKey -> Triple(trackId.takeIf { enabled }, enabled, refreshKey) }
        .flatMapLatest { (trackId, _, _) ->
            flow {
                if (trackId == null || trackId <= 0L) {
                    loadedCommentsTrackId = null
                    optimisticComments.value = emptyList()
                    emit(emptyList())
                    return@flow
                }
                if (trackId != loadedCommentsTrackId) {
                    loadedCommentsTrackId = trackId
                    optimisticComments.value = emptyList()
                    removedCommentIds.value = emptySet()
                    emit(emptyList())
                }
                emit(runCatching { trackRepository.getTrackComments(trackId) }.getOrDefault(emptyList()))
            }
        }

    val timedComments = combine(fetchedComments, optimisticComments, removedCommentIds) { fetched, extra, removed ->
        val pending = extra.filter { local ->
            fetched.none { it.id == local.id || (it.body == local.body && it.timestampMs == local.timestampMs) }
        }
        (fetched + pending).filterNot { it.id in removed }.sortedBy { it.timestampMs ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seekBarStyle = settingsRepository.settings.map { it.seekBarStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeekBarStyle.CLASSIC)

    val backgroundMode = settingsRepository.settings.map { it.backgroundMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.savoo.scclient.data.repository.AppBackgroundMode.DYNAMIC)

    val playerHintShown = settingsRepository.settings.map { it.playerHintShown }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pixelGlowEnabled = settingsRepository.settings.map { it.pixelGlowEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dismissPlayerHint() {
        viewModelScope.launch { settingsRepository.setPlayerHintShown(true) }
    }

    val playerStyle = settingsRepository.settings.map { it.playerStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.savoo.scclient.data.repository.PlayerStyle.PIXEL)

    val artworkScale = settingsRepository.settings.map { it.artworkScalePercent / 100f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    val artworkRingEnabled = settingsRepository.settings.map { it.artworkRingEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val artworkShape = settingsRepository.settings.map { it.artworkShape }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.savoo.scclient.data.repository.ArtworkShape.BLOB)

    val playerBackgroundStyle = settingsRepository.settings.map { it.playerBackgroundStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.savoo.scclient.data.repository.PlayerBackgroundStyle.ORB)

    val activeLyricsLine = combine(controller.state, lyrics, lyricsSync) { state, result, syncState ->
        val lines = (result as? LyricsResult.Synced)?.lines
        if (lines.isNullOrEmpty()) -1
        else {
            val lyricsTimeMs = syncState.sync.lyricsTimeAt(state.positionMs) + 200
            lines.indexOfLast { it.timeMs <= lyricsTimeMs }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    fun onLyricsSyncAction(action: LyricsSyncAction) {
        val track = controller.state.value.currentTrack ?: return
        val current = lyricsSync.value.sync
        val automatic = lyricsSync.value.automatic
        when (action) {
            is LyricsSyncAction.AdjustOffset -> saveLyricsSync(track, current.withOffset(current.offsetMs + action.deltaMs))
            LyricsSyncAction.ResetOffset -> saveLyricsSync(track, current.withOffset(automatic.offsetMs))
            is LyricsSyncAction.AdjustDrift -> saveLyricsSync(track, current.withDrift(current.driftMsPerMin + action.deltaMsPerMin))
            LyricsSyncAction.ResetDrift -> saveLyricsSync(track, current.withDrift(automatic.driftMsPerMin))
        }
    }

    private fun saveLyricsSync(track: Track, sync: LyricsSync) {
        val syncedLyrics = lyrics.value as? LyricsResult.Synced
        val automatic = lyricsSync.value.automatic
        val trackDurationMs = track.durationMs.takeIf { it > 0L } ?: controller.state.value.durationMs.takeIf { it > 0L }
        pendingLyricsSync.value = track.id to sync
        viewModelScope.launch {
            lyricsSyncWriteLock.withLock {
                lyricsRepository.saveSync(track, trackDurationMs, sync, automatic, syncedLyrics)
            }
        }
    }

    private val _isSavingOffline = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSavingOffline = _isSavingOffline

    private val _offlineSaveResult = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    val offlineSaveResult = _offlineSaveResult

    fun consumeOfflineSaveResult() {
        _offlineSaveResult.value = null
    }

    fun toggleFavorite() {
        val track = controller.state.value.currentTrack ?: return
        viewModelScope.launch {
            favoritesRepository.toggleTrackFavorite(track)
        }
    }

    fun toggleFavoriteWithUndo() {
        val track = controller.state.value.currentTrack ?: return
        viewModelScope.launch {
            val repository = favoritesRepository
            val previous = repository.getTrackFavorite(track.id)
            undoController.show(
                UndoAction(
                    messageRes = if (previous == null) R.string.favorite_added else R.string.favorite_removed,
                    icon = if (previous == null) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    onUndo = { repository.restoreTrackFavorite(track, previous) },
                )
            )
            repository.toggleTrackFavorite(track)
        }
    }

    val bulkDownload = offlineTrackManager.bulkDownload

    fun cancelBulkDownloads() = offlineTrackManager.cancelBulkDownloads()

    fun setBulkParallel(enabled: Boolean) = offlineTrackManager.setBulkParallel(enabled)

    val favoritesImport = favoritesImportManager.state

    fun cancelFavoritesImport() = favoritesImportManager.cancel()

    fun saveForOffline() {
        val track = controller.state.value.currentTrack ?: return
        viewModelScope.launch {
            android.util.Log.d("OfflineTrack", "Starting save for: ${track.title} (media=${track.media != null})")
            _isSavingOffline.value = true
            val result = offlineTrackManager.saveForOffline(track)
            result.onSuccess { android.util.Log.d("OfflineTrack", "Save success") }
            result.onFailure { android.util.Log.e("OfflineTrack", "Save failed: ${it.message}") }
            _isSavingOffline.value = false
            _offlineSaveResult.value = result.isSuccess
        }
    }

    fun removeFromOffline() {
        val track = controller.state.value.currentTrack ?: return
        viewModelScope.launch {
            offlineTrackManager.removeFromOffline(track.id)
        }
    }

    fun excludeCurrentArtistAndSkip() {
        val track = controller.state.value.currentTrack ?: return
        if (track.user.id == 0L) return
        viewModelScope.launch {
            excludedArtistDao.exclude(ExcludedMixArtist(artistId = track.user.id, username = track.user.username))
        }
        controller.excludeArtistFromQueue(track.user.id, track.user.username)
    }
}
