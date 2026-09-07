package com.savoo.scclient.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.MoreExecutors
import com.savoo.scclient.R
import com.savoo.scclient.data.local.PlayHistoryDao
import com.savoo.scclient.data.local.UnavailableTrackDao
import com.savoo.scclient.data.model.PlayEvent
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.UnavailableReason
import com.savoo.scclient.data.model.UnavailableTrackEntity
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.ConnectivityEventBus
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.debug.DebugLog
import retrofit2.HttpException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import androidx.compose.ui.graphics.Color

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val loadingTrackId: Long? = null,
    val isRetryingNetwork: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    // Which playQueue() call is currently active, e.g. "home_mix" - lets a caller tell "is MY queue
    // the one playing right now" apart from "does the current track merely also appear in my queue"
    // (the two look the same if you only check track membership, but they're not: e.g. a track played
    // from Jump Back In can also be part of the mix's pool without the mix itself being what's active).
    val queueTag: String? = null,
    // Index into the current queue, kept separate from currentTrack.id because a queue can legitimately
    // hold the same track id twice (playlists do this) - the id alone can't disambiguate which copy is
    // actually playing, which the Queue screen needs to highlight the right row.
    val queueIndex: Int = -1,
)

// Wraps a Track with a synthetic id that's stable for the life of this queue slot (assigned once, when
// the entry is added) and untouched by later moves/removes - unlike Track.id, which a queue can hold
// duplicates of (playlists do this), a QueueEntry.id always identifies exactly one slot, which is what
// the Queue screen's drag-to-reorder needs for smooth, uninterrupted dragging across list positions.
data class QueueEntry(val id: Long, val track: Track)

data class SkippedTrack(val track: Track, val reason: UnavailableReason?)

@UnstableApi
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackRepository: TrackRepository,
    private val trackCache: TrackCache,
    val offlineTrackManager: OfflineTrackManager,
    private val playHistoryDao: PlayHistoryDao,
    private val settingsRepository: SettingsRepository,
    private val unavailableTrackDao: UnavailableTrackDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var positionJob: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()
    private val _seedColor = MutableStateFlow<Color?>(null)
    val seedColor = _seedColor.asStateFlow()
    private val _skippedTrackEvents = MutableSharedFlow<SkippedTrack>(extraBufferCapacity = 1)
    val skippedTrackEvents = _skippedTrackEvents.asSharedFlow()
    val unavailableReasons: StateFlow<Map<Long, UnavailableReason>> = unavailableTrackDao.observeAll()
        .map { list -> list.associate { it.trackId to it.reason } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())
    private val _queueEntries = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queueEntries = _queueEntries.asStateFlow()
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs = _sleepTimerRemainingMs.asStateFlow()
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val queue = mutableListOf<QueueEntry>()
    private var nextQueueEntryId = 0L
    private var queueIndex = -1
    private var currentQueueTag: String? = null
    private var playRequestId = 0L
    // Snapshot of queue's order right before shuffling, so toggling shuffle off can restore it. Empty when shuffle is off.
    // Shuffling is handled entirely at this level (queue itself is reordered) rather than via ExoPlayer's native
    // shuffleModeEnabled - replaceMediaItem() turned out to internally remove+reinsert the period, which mutates
    // ExoPlayer's own shuffle order as a side effect, so "previous" could land somewhere unexpected after any
    // background resolve had happened. Keeping native shuffle permanently off avoids that entirely.
    private val originalOrder = mutableListOf<QueueEntry>()
    private val recentTracks = mutableListOf<Track>()
    private val prefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
    private val errorRetryCount = mutableMapOf<Long, Int>()
    private val resolvingMediaIds = mutableSetOf<Long>()
    // Serializes replaceMediaItem/prepare/play calls to the (IPC-backed) MediaController - concurrent preload
    // resolutions for the next and previous track were issuing these commands at overlapping times, which is
    // suspected to be involved in the shuffle-skip cascade (the player jumping through several unrelated tracks).
    private val playerCommandMutex = Mutex()
    private var isScrubbing = false
    private var wasPlayingBeforeScrub = false
    private var lastScrubSeekAtMs = 0L
    private var pendingScrubJob: kotlinx.coroutines.Job? = null
    private var fadeJob: kotlinx.coroutines.Job? = null
    private var fadeOutStartedForMediaId: Long? = null
    private var crossfadeEnabled = false

    private var listenTrackId: Long? = null
    private var listenTitle: String = ""
    private var listenArtistId: Long = 0L
    private var listenArtistName: String = ""
    private var listenArtworkUrl: String? = null
    private var listenGenre: String? = null
    private var listenAccumulatedMs: Long = 0L
    private var listenSegmentStartRealtime: Long? = null
    private val minListenMsToRecord = 5_000L

    private companion object {
        const val TAG = "PlayerController"
        private const val PENDING_SCHEME = "rawresource"
        private const val SCRUB_THROTTLE_MS = 140L
        private const val FADE_OUT_LEAD_MS = 1500L
        private const val FADE_IN_MS = 900L
        private const val FADE_STEP_MS = 60L

        // Placeholder items point at a real, always-loadable local silent file rather than an invalid URI (which used
        // to make ExoPlayer choke while probing it during buffering/look-ahead). Every placeholder gets a distinct
        // fragment (#<trackId>) so each is a unique Uri - dozens of playlist entries sharing byte-identical URIs was
        // itself confusing ExoPlayer's Timeline/MediaSource bookkeeping and causing shuffle skips to cascade through
        // many unrelated tracks; the fragment doesn't affect how RawResourceDataSource resolves the resource id.
        fun pendingUriFor(trackId: Long): Uri =
            RawResourceDataSource.buildRawResourceUri(R.raw.silence).buildUpon().fragment(trackId.toString()).build()
    }

    private fun isPending(mediaItem: MediaItem?): Boolean =
        mediaItem?.localConfiguration?.uri?.scheme == PENDING_SCHEME

    private fun wrap(track: Track): QueueEntry = QueueEntry(nextQueueEntryId++, track)
    private fun wrapAll(tracks: List<Track>): List<QueueEntry> = tracks.map { wrap(it) }

    // A queue can legitimately hold the same track twice (playlists do this), so a plain indexOf/find
    // by value would resolve every duplicate to the first copy and swap the URL onto the wrong window.
    // When the id being looked up is what's playing right now, the player's own index is authoritative.
    private fun queueIndexOf(mediaId: Long): Int {
        controller?.let { c ->
            val current = c.currentMediaItemIndex
            if (current in queue.indices && queue[current].track.id == mediaId) return current
        }
        return queue.indexOfFirst { it.track.id == mediaId }
    }

    init {
        restoreState()
        scope.launch {
            settingsRepository.settings.map { it.crossfadeEnabled }.distinctUntilChanged().collect { enabled ->
                crossfadeEnabled = enabled
                if (!enabled) {
                    fadeJob?.cancel()
                    fadeOutStartedForMediaId = null
                    controller?.volume = 1f
                }
            }
        }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(playerListener)
            controller?.shuffleModeEnabled = false // shuffling is handled at the queue level, see toggleShuffle()
            controller?.repeatMode = prefs.getInt("repeat_mode", Player.REPEAT_MODE_OFF)
            _state.update {
                it.copy(
                    shuffleEnabled = prefs.getBoolean("shuffle_enabled", false),
                    repeatMode = controller?.repeatMode ?: Player.REPEAT_MODE_OFF,
                )
            }
            restorePlayLastTrack()
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                _state.update { it.copy(isPlaying = true, loadingTrackId = null) }
                controller?.currentMediaItem?.mediaId?.toLongOrNull()?.let { errorRetryCount.remove(it) }
                resumeListenSegment()
                startPositionPolling()
            } else {
                _state.update { it.copy(isPlaying = false) }
                stopPositionPolling()
                pauseListenSegment()
                saveState()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            DebugLog.log(TAG, "onPlaybackStateChanged: $playbackState currentId=${controller?.currentMediaItem?.mediaId} pos=${controller?.currentPosition} dur=${controller?.duration}")
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            if (playbackState == Player.STATE_READY) updatePosition()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            DebugLog.log(TAG, "onPositionDiscontinuity: reason=$reason oldItem=${oldPosition.mediaItem?.mediaId} oldPos=${oldPosition.positionMs} newItem=${newPosition.mediaItem?.mediaId} newPos=${newPosition.positionMs}")
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            DebugLog.log(TAG, "onTimelineChanged: reason=$reason windowCount=${timeline.windowCount}")
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            DebugLog.log(TAG, "Playback error: ${error.message}")
            val mediaId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
            if (mediaId in resolvingMediaIds) return // an in-flight resolution will prepare()/play() or skip once it finishes
            val attempts = (errorRetryCount[mediaId] ?: 0) + 1
            errorRetryCount[mediaId] = attempts
            val track = queue.find { it.track.id == mediaId }?.track
            if (attempts > 2) {
                DebugLog.log(TAG, "Giving up on track $mediaId after $attempts failed attempts, skipping")
                errorRetryCount.remove(mediaId)
                if (track != null) scope.launch { skipDueToFailure(track) } else controller?.seekToNext()
                return
            }
            if (track == null) {
                scope.launch {
                    delay(400)
                    controller?.prepare()
                }
                return
            }
            resolvingMediaIds.add(mediaId)
            scope.launch {
                try {
                    delay(400)
                    val resolved = resolveTrack(track.copy(media = null))
                    if (resolved == null) {
                        skipDueToFailure(track)
                        return@launch
                    }
                    val i = queueIndexOf(mediaId)
                    if (i < 0) return@launch
                    playerCommandMutex.withLock {
                        controller?.replaceMediaItem(i, buildMediaItem(resolved.track, resolved.url, resolved.isHls))
                        controller?.let {
                            if (it.currentMediaItem?.mediaId?.toLongOrNull() == mediaId) {
                                it.seekTo(i, 0L)
                                it.prepare()
                                if (it.playWhenReady) it.play()
                            }
                        }
                    }
                    if (resolved.needsCaching) {
                        trackCache.cacheAudioFile(resolved.track, resolved.url)
                    }
                } finally {
                    resolvingMediaIds.remove(mediaId)
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatMode = repeatMode) }
            updateQueueState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val mediaId = mediaItem?.mediaId?.toLongOrNull() ?: return
            val track = queue.firstOrNull { it.track.id == mediaId }?.track
            DebugLog.log(TAG, "onMediaItemTransition: id=$mediaId title=${track?.title} reason=$reason pending=${isPending(mediaItem)} queueIndex(before)=$queueIndex")
            if (track != null) {
                val idx = queueIndexOf(mediaId)
                if (idx >= 0) {
                    queueIndex = idx
                    updateQueueState()
                }

                // replaceMediaItem() (used to swap a resolved "pending" placeholder for the real URL) fires this same
                // callback with reason=PLAYLIST_CHANGED even when nothing the user asked for actually changed. Treating
                // that as a real transition was the cause of the cover/title flashing: it re-triggered preloadAdjacent(),
                // which replaced more items, which fired more PLAYLIST_CHANGED transitions - a self-sustaining cascade.
                val isSelfEcho = reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                    _state.value.currentTrack?.id == mediaId
                if (!isSelfEcho) {
                    beginListenSegment(track)
                    _state.update { it.copy(currentTrack = track, durationMs = track.durationMs, loadingTrackId = null) }
                    extractSeedColor(track.artworkUrl)
                    fadeOutStartedForMediaId = null
                    fadeJob?.cancel()
                    if (crossfadeEnabled) {
                        controller?.volume = 0f
                        startFade(from = 0f, to = 1f, durationMs = FADE_IN_MS)
                    } else {
                        controller?.volume = 1f
                    }
                }

                if (isPending(mediaItem) && resolvingMediaIds.add(mediaId)) {
                    DebugLog.log(TAG, "reactive resolve start: id=$mediaId title=${track.title}")
                    scope.launch {
                        try {
                            val resolved = resolveTrack(track)
                            if (resolved == null) {
                                DebugLog.log(TAG, "reactive resolve FAILED after retries, skipping: id=$mediaId title=${track.title}")
                                skipDueToFailure(track)
                                return@launch
                            }
                            DebugLog.log(TAG, "reactive resolve OK: id=$mediaId title=${track.title}")

                            val i = queueIndexOf(mediaId)
                            if (i < 0) return@launch

                            playerCommandMutex.withLock {
                                controller?.replaceMediaItem(i, buildMediaItem(resolved.track, resolved.url, resolved.isHls))

                                // Only do this if we're still actually the current item - playback may have moved on to a
                                // different (possibly still-pending) track while this resolve was in flight. replaceMediaItem
                                // on the ACTIVE window doesn't actually swap the audio being read: ExoPlayer keeps decoding
                                // whatever was already buffered from the placeholder (~0.5s of silence) until that period
                                // naturally "ends", THEN auto-advances - which is what was really driving the skip cascade.
                                // seekTo(sameIndex, 0) forces it to actually discard that and reload from the new source.
                                controller?.let {
                                    if (it.currentMediaItem?.mediaId?.toLongOrNull() == mediaId) {
                                        it.seekTo(i, 0L)
                                        it.prepare()
                                        if (it.playWhenReady) it.play()
                                    }
                                }
                            }

                            if (resolved.needsCaching) {
                                trackCache.cacheAudioFile(resolved.track, resolved.url)
                            }
                        } finally {
                            resolvingMediaIds.remove(mediaId)
                        }
                    }
                }

                // Don't re-trigger preloading off our own PLAYLIST_CHANGED echoes - see the isSelfEcho comment above.
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    preloadAdjacent()
                }
            }
        }
    }

    private data class ResolvedTrack(val track: Track, val url: String, val needsCaching: Boolean, val isHls: Boolean = false)

    // Single source of truth for turning a queue entry into a playable URL: offline file, then disk cache, then network -
    // retrying the network step a couple of times before giving up, so a transient hiccup doesn't silently skip the track.
    private suspend fun markUnavailable(trackId: Long, reason: UnavailableReason) {
        withContext(Dispatchers.IO) {
            unavailableTrackDao.mark(UnavailableTrackEntity(trackId, reason, System.currentTimeMillis()))
        }
        while (true) {
            val index = queue.indexOfFirst { it.track.id == trackId }
            if (index < 0) break
            removeQueueItem(index)
        }
    }

    private suspend fun resolveTrack(track: Track, attempts: Int = 3, reportProgress: Boolean = true): ResolvedTrack? {
        offlineTrackManager.getLocalPath(track.id)?.let { return ResolvedTrack(track, it, needsCaching = false) }
        trackCache.getCachedFilePath(track.id)?.let { return ResolvedTrack(track, it, needsCaching = false) }

        withContext(Dispatchers.IO) { unavailableTrackDao.get(track.id) }?.let {
            DebugLog.log(TAG, "resolveTrack: id=${track.id} title=${track.title} already marked ${it.reason}, skipping")
            return null
        }

        val effectiveAttempts = if (ConnectivityEventBus.isUnreachable.value) 1 else attempts
        try {
            repeat(effectiveAttempts) { attempt ->
                if (attempt > 0 && reportProgress) _state.update { it.copy(isRetryingNetwork = true) }
                var deleted = false
                val fullTrack = if (attempt == 0 && track.media != null) track
                    else withContext(Dispatchers.IO) {
                        runCatching { trackRepository.getTrack(track.id) }
                            .onFailure { if (it is HttpException && it.code() == 404) deleted = true }
                            .getOrNull()
                    } ?: track
                if (deleted) {
                    DebugLog.log(TAG, "resolveTrack id=${track.id} title=${track.title}: getTrack() 404, marking DELETED")
                    markUnavailable(track.id, UnavailableReason.DELETED)
                    return null
                }
                if (fullTrack.media == null) {
                    DebugLog.log(TAG, "resolveTrack attempt=${attempt + 1}/$effectiveAttempts id=${track.id} title=${track.title}: getTrack() returned no media")
                }
                val transcodings = fullTrack.media?.transcodings
                if (!transcodings.isNullOrEmpty() && transcodings.none { it.format.protocol == "progressive" || it.format.protocol == "hls" }) {
                    DebugLog.log(TAG, "resolveTrack id=${track.id} title=${track.title}: only encrypted transcodings, marking DRM")
                    markUnavailable(track.id, UnavailableReason.DRM)
                    return null
                }
                val streamResult = withContext(Dispatchers.IO) {
                    runCatching { trackRepository.resolvePlayableStream(fullTrack) }
                        .onFailure { DebugLog.log(TAG, "resolveTrack attempt=${attempt + 1}/$effectiveAttempts id=${track.id} title=${track.title}: resolvePlayableStream threw ${it}") }
                }
                streamResult.getOrNull()?.let { stream ->
                    return ResolvedTrack(fullTrack, stream.url, needsCaching = !stream.isHls, isHls = stream.isHls)
                }
                val streamFailure = streamResult.exceptionOrNull()
                if (streamFailure is HttpException && streamFailure.code() == 404) {
                    DebugLog.log(TAG, "resolveTrack id=${track.id} title=${track.title}: all playable formats 404 on resolve, marking DRM")
                    markUnavailable(track.id, UnavailableReason.DRM)
                    return null
                }
                if (attempt < effectiveAttempts - 1) delay(500L * (attempt + 1))
            }
            DebugLog.log(TAG, "resolveTrack GAVE UP after $effectiveAttempts attempts: id=${track.id} title=${track.title}")
            return null
        } finally {
            if (reportProgress) _state.update { it.copy(isRetryingNetwork = false) }
        }
    }

    private suspend fun skipDueToFailure(track: Track) {
        if (ConnectivityEventBus.isUnreachable.value) {
            DebugLog.log(TAG, "skipDueToFailure: id=${track.id} title=${track.title} offline, pausing instead of skipping")
            controller?.pause()
            _state.update { it.copy(loadingTrackId = null, isBuffering = false) }
            return
        }
        val reason = withContext(Dispatchers.IO) { unavailableTrackDao.get(track.id) }?.reason
        DebugLog.log(TAG, "skipDueToFailure: id=${track.id} title=${track.title} reason=$reason")
        _skippedTrackEvents.tryEmit(SkippedTrack(track, reason))
        controller?.seekToNext()
    }

    private fun buildMediaItem(track: Track, url: String, isHls: Boolean = false): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .apply { if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.user.username)
                    .setArtworkUri(track.artworkUrl?.replace("-large", "-t500x500")?.let { Uri.parse(it) })
                    .build()
            ).build()

    private fun buildPendingMediaItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(pendingUriFor(track.id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.user.username)
                    .setArtworkUri(track.artworkUrl?.replace("-large", "-t500x500")?.let { Uri.parse(it) })
                    .build()
            ).build()

    // Preloads neighbours via media3's shuffle/repeat-aware next/previous index so skips rarely hit an unresolved "pending" item.
    private fun preloadAdjacent() {
        val c = controller ?: return
        val indices = listOfNotNull(
            c.nextMediaItemIndex.takeIf { it != androidx.media3.common.C.INDEX_UNSET },
            c.previousMediaItemIndex.takeIf { it != androidx.media3.common.C.INDEX_UNSET },
        )
        DebugLog.log(TAG, "preloadAdjacent: currentIndex=${c.currentMediaItemIndex} candidates=$indices shuffle=${c.shuffleModeEnabled}")
        for (idx in indices) {
            if (idx !in 0 until c.mediaItemCount) continue
            val item = runCatching { c.getMediaItemAt(idx) }.getOrNull() ?: continue
            if (isPending(item)) {
                item.mediaId.toLongOrNull()?.let { resolveAndReplace(it, idx) }
            }
        }
    }

    private fun resolveAndReplace(mediaId: Long, preloadIndex: Int) {
        if (!resolvingMediaIds.add(mediaId)) {
            DebugLog.log(TAG, "resolveAndReplace: id=$mediaId already in flight, skipping duplicate")
            return
        }
        DebugLog.log(TAG, "preload resolve start: id=$mediaId")
        scope.launch {
            try {
                val track = queue.getOrNull(preloadIndex)?.track?.takeIf { it.id == mediaId }
                    ?: queue.firstOrNull { it.track.id == mediaId }?.track
                    ?: return@launch
                val resolved = resolveTrack(track, reportProgress = false)
                if (resolved == null) {
                    DebugLog.log(TAG, "preload resolve FAILED: id=$mediaId title=${track.title}")
                    return@launch
                }
                DebugLog.log(TAG, "preload resolve OK: id=$mediaId title=${track.title}")

                // The queue may have been reordered (shuffle) or rebuilt while this was resolving, so the
                // index we prefetched for can now hold a different track - only replace if it still matches.
                val idx = preloadIndex.takeIf { queue.getOrNull(it)?.track?.id == mediaId } ?: queueIndexOf(mediaId)
                if (idx < 0) return@launch

                playerCommandMutex.withLock {
                    val stillThere = controller?.let { c ->
                        idx < c.mediaItemCount && runCatching { c.getMediaItemAt(idx).mediaId.toLongOrNull() }.getOrNull() == mediaId
                    } ?: false
                    if (!stillThere) return@withLock
                    controller?.replaceMediaItem(idx, buildMediaItem(resolved.track, resolved.url, resolved.isHls))

                    // If this neighbour became the current item while we were resolving it, recover it the same way the
                    // reactive path does - see the comment there for why seekTo() is needed, not just prepare().
                    controller?.let {
                        if (it.currentMediaItem?.mediaId?.toLongOrNull() == mediaId) {
                            it.seekTo(idx, 0L)
                            it.prepare()
                            if (it.playWhenReady) it.play()
                        }
                    }
                }

                if (resolved.needsCaching) {
                    trackCache.cacheAudioFile(resolved.track, resolved.url)
                }
            } finally {
                resolvingMediaIds.remove(mediaId)
            }
        }
    }

    private fun startPositionPolling() {
        stopPositionPolling()
        positionJob = scope.launch {
            var tick = 0
            while (true) {
                updatePosition()
                tick++
                if (tick % 6 == 0) saveState()
                delay(500L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun updatePosition() {
        val pos = controller?.currentPosition ?: return
        val dur = controller?.duration ?: return
        _state.update { it.copy(positionMs = pos, durationMs = dur.coerceAtLeast(0L)) }
        maybeStartFadeOut(pos, dur)
    }

    private fun maybeStartFadeOut(pos: Long, dur: Long) {
        if (dur <= 0 || isScrubbing || !crossfadeEnabled) return
        val mediaId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val remaining = dur - pos
        if (remaining in 0..FADE_OUT_LEAD_MS && fadeOutStartedForMediaId != mediaId) {
            fadeOutStartedForMediaId = mediaId
            startFade(from = controller?.volume ?: 1f, to = 0f, durationMs = remaining)
        } else if (remaining > FADE_OUT_LEAD_MS && fadeOutStartedForMediaId == mediaId) {
            fadeOutStartedForMediaId = null
            fadeJob?.cancel()
            controller?.volume = 1f
        }
    }

    private fun beginListenSegment(track: Track) {
        flushListenSegment()
        listenTrackId = track.id
        listenTitle = track.title
        listenArtistId = track.user.id
        listenArtistName = track.user.username
        listenArtworkUrl = track.artworkUrl
        listenGenre = track.genre
        listenAccumulatedMs = 0L
        listenSegmentStartRealtime = if (controller?.isPlaying == true) System.currentTimeMillis() else null
    }

    private fun resumeListenSegment() {
        if (listenTrackId != null && listenSegmentStartRealtime == null) {
            listenSegmentStartRealtime = System.currentTimeMillis()
        }
    }

    private fun pauseListenSegment() {
        val start = listenSegmentStartRealtime ?: return
        listenAccumulatedMs += System.currentTimeMillis() - start
        listenSegmentStartRealtime = null
    }

    private fun flushListenSegment() {
        pauseListenSegment()
        val trackId = listenTrackId
        val ms = listenAccumulatedMs
        if (trackId != null && ms >= minListenMsToRecord) {
            val event = PlayEvent(
                trackId = trackId,
                title = listenTitle,
                artistId = listenArtistId,
                artistName = listenArtistName,
                artworkUrl = listenArtworkUrl,
                msPlayed = ms,
                playedAt = System.currentTimeMillis(),
                genre = listenGenre,
            )
            scope.launch(Dispatchers.IO) { runCatching { playHistoryDao.insert(event) } }
        }
        listenTrackId = null
        listenAccumulatedMs = 0L
        listenSegmentStartRealtime = null
    }

    private fun startFade(from: Float, to: Float, durationMs: Long) {
        fadeJob?.cancel()
        if (durationMs <= 0) {
            controller?.volume = to
            return
        }
        fadeJob = scope.launch {
            val steps = (durationMs / FADE_STEP_MS).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                controller?.volume = from + (to - from) * (i.toFloat() / steps)
                delay(FADE_STEP_MS)
            }
            controller?.volume = to
        }
    }

    fun play(track: Track) {
        currentQueueTag = null
        _state.update { it.copy(loadingTrackId = track.id) }
        val idx = queue.indexOfFirst { it.track.id == track.id }
        val idxForThisTrack = if (idx >= 0) idx else {
            queue.add(wrap(track))
            queue.lastIndex
        }
        queueIndex = idxForThisTrack
        updateQueueState()
        doPlay(track, idxForThisTrack, ++playRequestId)
    }

    fun retryTrack(track: Track) {
        val idx = queue.indexOfFirst { it.track.id == track.id }
        if (idx >= 0) playFromQueue(idx) else play(track)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, repeatAll: Boolean = false, tag: String? = null) {
        if (tracks.isEmpty()) return
        val clampedStart = startIndex.coerceIn(0, tracks.lastIndex)
        currentQueueTag = tag

        // Session-only, not persisted to prefs (unlike cycleRepeatMode()) - a queue that wants to loop
        // (e.g. the Home mix, which should feel near-endless) shouldn't silently change the user's
        // saved repeat preference for every other queue they play afterwards.
        if (repeatAll) {
            controller?.repeatMode = Player.REPEAT_MODE_ALL
        }

        // Starting an unrelated queue (e.g. a different playlist) while shuffle is on used to leave
        // shuffleEnabled=true and originalOrder pointing at the PREVIOUS queue: the toggle looked on
        // but this queue played in order, and turning shuffle off would jump back to the old queue's
        // tracks. Re-shuffle the new queue up front instead, same as toggleShuffle()'s own algorithm.
        if (_state.value.shuffleEnabled) {
            val wrapped = wrapAll(tracks)
            val startEntry = wrapped[clampedStart]
            originalOrder.clear()
            originalOrder.addAll(wrapped)
            val rest = wrapped.filterIndexed { i, _ -> i != clampedStart }.shuffled()
            queue.clear()
            queue.add(startEntry)
            queue.addAll(rest)
            queueIndex = 0
        } else {
            originalOrder.clear()
            queue.clear()
            queue.addAll(wrapAll(tracks))
            queueIndex = clampedStart
        }

        _state.update { it.copy(loadingTrackId = queue[queueIndex].track.id) }
        updateQueueState()
        doPlay(queue[queueIndex].track, queueIndex, ++playRequestId)
    }

    fun skipToNext() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNext() }
    }

    fun excludeArtistFromQueue(artistId: Long, artistUsername: String) {
        val usernameLower = artistUsername.trim().lowercase()
        fun matches(t: Track): Boolean =
            t.user.id == artistId || (usernameLower.isNotEmpty() && t.title.lowercase().contains(usernameLower))

        if (queue.isEmpty() || queue.none { matches(it.track) }) return

        var resumeTrack: Track? = null
        for (offset in 1..queue.size) {
            val candidate = queue[(queueIndex + offset) % queue.size].track
            if (!matches(candidate)) {
                resumeTrack = candidate
                break
            }
        }

        val filteredQueue = queue.filterNot { matches(it.track) }
        if (filteredQueue.isEmpty()) {
            queue.clear()
            originalOrder.clear()
            queueIndex = -1
            currentQueueTag = null
            controller?.stop()
            controller?.clearMediaItems()
            flushListenSegment()
            _queueEntries.value = emptyList()
            _state.update {
                it.copy(currentTrack = null, isPlaying = false, isBuffering = false, hasNext = false, hasPrev = false, queueTag = null, queueIndex = -1)
            }
            return
        }

        if (originalOrder.isNotEmpty()) {
            val filteredOriginal = originalOrder.filterNot { matches(it.track) }
            originalOrder.clear()
            originalOrder.addAll(filteredOriginal)
        }

        queue.clear()
        queue.addAll(filteredQueue)
        queueIndex = resumeTrack?.let { rt -> queue.indexOfFirst { it.track.id == rt.id } }?.takeIf { it >= 0 } ?: 0

        _state.update { it.copy(loadingTrackId = queue[queueIndex].track.id) }
        updateQueueState()
        doPlay(queue[queueIndex].track, queueIndex, ++playRequestId)
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    // Repositions a queue entry without touching what's actually playing - moveMediaItem() re-indexes the
    // existing window in place (no re-resolve, no audible hiccup), unlike the setMediaItems()-based rebuilds
    // used elsewhere in this file for bulk queue changes.
    fun moveQueueItem(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val item = queue.removeAt(from)
        queue.add(to, item)
        queueIndex = when {
            from == queueIndex -> to
            from < queueIndex && to >= queueIndex -> queueIndex - 1
            from > queueIndex && to <= queueIndex -> queueIndex + 1
            else -> queueIndex
        }
        controller?.moveMediaItem(from, to)
        // Belt-and-suspenders resync: MediaController mirrors the session's player state locally and
        // synchronously for commands it issues itself, so this reflects the post-move reality immediately.
        controller?.currentMediaItemIndex?.takeIf { it in queue.indices }?.let { queueIndex = it }
        updateQueueState()
    }

    // Removing the currently playing entry is the one case that can trigger a real onMediaItemTransition
    // (ExoPlayer auto-advances to whatever now occupies that slot) - queueIndex is deliberately NOT adjusted
    // by hand for that case below; it's resynced straight from the controller once the removal has applied.
    fun removeQueueItem(index: Int) {
        if (index !in queue.indices) return
        val removed = queue.removeAt(index)
        if (originalOrder.isNotEmpty()) {
            val i = originalOrder.indexOfFirst { it.id == removed.id }
            if (i >= 0) originalOrder.removeAt(i)
        }
        if (queue.isEmpty()) {
            queueIndex = -1
            currentQueueTag = null
            controller?.stop()
            controller?.clearMediaItems()
            flushListenSegment()
            _queueEntries.value = emptyList()
            _state.update {
                it.copy(currentTrack = null, isPlaying = false, isBuffering = false, hasNext = false, hasPrev = false, queueTag = null, queueIndex = -1)
            }
            return
        }
        val wasCurrent = index == queueIndex
        if (!wasCurrent && index < queueIndex) queueIndex--
        controller?.removeMediaItem(index)
        if (wasCurrent) {
            queueIndex = controller?.currentMediaItemIndex?.takeIf { it in queue.indices } ?: index.coerceIn(0, queue.lastIndex)
        }
        updateQueueState()
    }

    // Jumps playback straight to an existing queue slot (tap-to-play in the Queue screen) without
    // reshuffling or otherwise touching the rest of the queue.
    fun playFromQueue(index: Int) {
        if (index !in queue.indices) return
        val entry = queue[index]
        queueIndex = index
        _state.update { it.copy(loadingTrackId = entry.track.id) }
        updateQueueState()
        doPlay(entry.track, index, ++playRequestId)
    }

    fun toggleShuffle() {
        val enabling = !_state.value.shuffleEnabled
        val current = queue.getOrNull(queueIndex)

        if (enabling) {
            originalOrder.clear()
            originalOrder.addAll(queue)
            val rest = queue.filterIndexed { i, _ -> i != queueIndex }.shuffled()
            queue.clear()
            current?.let { queue.add(it) }
            queue.addAll(rest)
            queueIndex = 0
        } else if (originalOrder.isNotEmpty()) {
            queue.clear()
            queue.addAll(originalOrder)
            originalOrder.clear()
            queueIndex = current?.let { c -> queue.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0
        }
        // `current`/originalOrder entries are matched by QueueEntry.id above, not Track.id - correctly
        // disambiguates duplicate tracks in the queue, unlike matching on the track's own id would.

        rebuildTimelinePreservingPlayback()
        _state.update { it.copy(shuffleEnabled = enabling) }
        prefs.edit().putBoolean("shuffle_enabled", enabling).apply()
    }

    // Rebuilds the controller's playlist to match `queue`'s current order without disturbing what's actually
    // playing: the current window reuses its already-resolved MediaItem (no re-fetch, no audible hiccup), every
    // other position goes back to a fresh "pending" placeholder to be resolved again as needed.
    private fun rebuildTimelinePreservingPlayback() {
        val c = controller ?: return
        val currentItem = c.currentMediaItem ?: return
        val pos = c.currentPosition
        val items = queue.mapIndexed { i, t -> if (i == queueIndex) currentItem else buildPendingMediaItem(t.track) }
        c.setMediaItems(items, queueIndex, pos)
        c.prepare()
        if (c.playWhenReady) c.play()
        updateQueueState()
        preloadAdjacent()
    }

    fun cycleRepeatMode() {
        controller?.let {
            val next = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = next
            prefs.edit().putInt("repeat_mode", next).apply()
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60_000L
            _sleepTimerRemainingMs.value = remaining
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1000L
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0L)
            }
            _sleepTimerRemainingMs.value = null
            controller?.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
    }

    private fun updateQueueState() {
        _queueEntries.value = queue.toList()
        _state.update {
            it.copy(
                hasNext = controller?.hasNextMediaItem() ?: (queueIndex < queue.lastIndex),
                hasPrev = controller?.hasPreviousMediaItem() ?: (queueIndex > 0),
                queueTag = currentQueueTag,
                queueIndex = queueIndex,
            )
        }
    }

    private fun doPlay(track: Track, requestIndex: Int, requestId: Long) {
        scope.launch {
            val resolved = resolveTrack(track)
            if (requestId != playRequestId) return@launch
            if (resolved == null) {
                _state.update { it.copy(loadingTrackId = null) }
                skipDueToFailure(track)
                return@launch
            }
            val (fullTrack, url, needsCaching, isHls) = resolved

            recentTracks.removeAll { it.id == fullTrack.id }
            recentTracks.add(fullTrack)
            if (recentTracks.size > 20) recentTracks.removeAt(0)

            playerCommandMutex.withLock {
                if (requestId != playRequestId) return@withLock

                // toggleShuffle() reorders `queue` without issuing a new play request, so requestIndex
                // can be stale by the time a slow resolve finishes - trusting it would drop this track's
                // real URL onto whatever now sits at that position and play the wrong song.
                val index = queue.indexOfFirst { it.track.id == fullTrack.id }
                    .takeIf { it >= 0 } ?: requestIndex.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
                queueIndex = index

                val allItems = queue.mapIndexed { i, t ->
                    if (i == index) buildMediaItem(fullTrack, url, isHls) else buildPendingMediaItem(t.track)
                }

                beginListenSegment(fullTrack)
                _state.update { it.copy(currentTrack = fullTrack, durationMs = fullTrack.durationMs, loadingTrackId = null) }
                extractSeedColor(fullTrack.artworkUrl)
                controller?.apply {
                    setMediaItems(allItems, index, 0L)
                    prepare()
                    play()
                }
                updateQueueState()
                preloadAdjacent()
            }

            if (needsCaching) {
                trackCache.cacheAudioFile(fullTrack, url)
            }
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun beginScrub() {
        isScrubbing = true
        wasPlayingBeforeScrub = controller?.isPlaying == true
        if (!wasPlayingBeforeScrub) controller?.play()
    }

    fun scrubTo(positionMs: Long) {
        if (!isScrubbing) return
        pendingScrubJob?.cancel()
        val now = System.currentTimeMillis()
        val elapsed = now - lastScrubSeekAtMs
        if (elapsed >= SCRUB_THROTTLE_MS) {
            lastScrubSeekAtMs = now
            controller?.seekTo(positionMs)
        } else {
            pendingScrubJob = scope.launch {
                delay(SCRUB_THROTTLE_MS - elapsed)
                lastScrubSeekAtMs = System.currentTimeMillis()
                controller?.seekTo(positionMs)
            }
        }
    }

    fun endScrub(positionMs: Long) {
        if (!isScrubbing) {
            controller?.seekTo(positionMs)
            return
        }
        pendingScrubJob?.cancel()
        pendingScrubJob = null
        isScrubbing = false
        controller?.seekTo(positionMs)
        if (!wasPlayingBeforeScrub) controller?.pause()
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0L

    fun release() {
        stopPositionPolling()
        sleepTimerJob?.cancel()
        flushListenSegment()
        saveState()
        controller?.release()
        controller = null
    }

    fun saveState() {
        val track = _state.value.currentTrack ?: return
        prefs.edit().apply {
            putString("last_track", trackToJson(track).toString())
            putLong("position_ms", _state.value.positionMs)
            val queueJson = JSONArray()
            queue.forEach { queueJson.put(trackToJson(it.track)) }
            putString("queue", queueJson.toString())
            putInt("queue_index", queueIndex)
            val recentJson = JSONArray()
            recentTracks.takeLast(20).forEach { recentJson.put(trackToJson(it)) }
            putString("recent", recentJson.toString())
            apply()
        }
    }

    private fun restoreState() {
        recentTracks.clear()
        val recentStr = prefs.getString("recent", null)
        if (recentStr != null) {
            try {
                val arr = JSONArray(recentStr)
                for (i in 0 until arr.length()) {
                    recentTracks.add(jsonToTrack(arr.getJSONObject(i)))
                }
            } catch (_: Exception) {}
        }
    }

    private fun restorePlayLastTrack() {
        val trackStr = prefs.getString("last_track", null)
        if (trackStr == null) return
        val position = prefs.getLong("position_ms", 0)
        try {
            val track = jsonToTrack(JSONObject(trackStr))
            val queueStr = prefs.getString("queue", null)
            val queueIdx = prefs.getInt("queue_index", 0)
            if (queueStr != null) {
                val arr = JSONArray(queueStr)
                queue.clear()
                for (i in 0 until arr.length()) {
                    queue.add(wrap(jsonToTrack(arr.getJSONObject(i))))
                }
                queueIndex = queueIdx.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
            }
            updateQueueState()
            scope.launch {
                val resolved = resolveTrack(track) ?: return@launch
                val (fullTrack, url, needsCaching, isHls) = resolved
                beginListenSegment(fullTrack)
                _state.update { it.copy(currentTrack = fullTrack, durationMs = fullTrack.durationMs, loadingTrackId = null) }
                extractSeedColor(fullTrack.artworkUrl)
                controller?.apply {
                    setMediaItem(buildMediaItem(fullTrack, url, isHls))
                    prepare()
                    if (position > 0) seekTo(position)
                }
                if (needsCaching) {
                    trackCache.cacheAudioFile(fullTrack, url)
                }
            }
        } catch (_: Exception) {}
    }

    fun getRecentTracks(): List<Track> = recentTracks.toList().reversed()

    private fun trackToJson(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("title", track.title)
        put("durationMs", track.durationMs)
        put("artworkUrl", track.artworkUrl)
        put("permalinkUrl", track.permalinkUrl)
        put("userId", track.user.id)
        put("username", track.user.username)
        put("userAvatarUrl", track.user.avatarUrl)
    }

    private fun jsonToTrack(obj: JSONObject): Track = Track(
        id = obj.getLong("id"),
        title = obj.getString("title"),
        durationMs = obj.getLong("durationMs"),
        artworkUrl = obj.optString("artworkUrl", null),
        permalinkUrl = obj.optString("permalinkUrl", null),
        user = User(
            id = obj.getLong("userId"),
            username = obj.getString("username"),
            avatarUrl = obj.optString("userAvatarUrl", null)
        )
    )

    private fun extractSeedColor(artworkUrl: String?) {
        if (artworkUrl == null) {
            _seedColor.value = null
            return
        }
        scope.launch {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl.replace("-large", "-t500x500"))
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch
                        if (swatch != null) {
                            _seedColor.value = Color(swatch.rgb)
                        }
                    }
                }
            } catch (_: Exception) {
                _seedColor.value = null
            }
        }
    }
}
