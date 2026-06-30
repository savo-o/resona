package com.savoo.scclient.player

import androidx.media3.common.MediaItem
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
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.MoreExecutors
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.TrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
)

@UnstableApi
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackRepository: TrackRepository,
    private val trackCache: TrackCache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var positionJob: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()
    private val _seedColor = MutableStateFlow<Color?>(null)
    val seedColor = _seedColor.asStateFlow()
    private val queue = mutableListOf<Track>()
    private var queueIndex = -1
    private val recentTracks = mutableListOf<Track>()
    private val prefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)

    init {
        restoreState()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(playerListener)
            restorePlayLastTrack()
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) startPositionPolling() else {
                stopPositionPolling()
                saveState()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_READY) updatePosition()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val mediaId = mediaItem?.mediaId?.toLongOrNull() ?: return
            val track = queue.find { it.id == mediaId }
            if (track != null) {
                val idx = queue.indexOf(track)
                if (idx >= 0) {
                    queueIndex = idx
                    updateQueueState()
                }
                _state.update { it.copy(currentTrack = track, durationMs = track.durationMs, loadingTrackId = null) }
                extractSeedColor(track.artworkUrl)

                if (mediaItem.localConfiguration?.uri?.toString() == "pending") {
                    scope.launch {
                        val cachedPath = trackCache.getCachedFilePath(mediaId)
                        val fullTrack = if (cachedPath != null) track
                            else if (track.media == null) {
                                runCatching { trackRepository.getTrack(track.id) }.getOrNull() ?: track
                            } else track
                        val url = if (cachedPath != null) cachedPath
                            else trackRepository.resolvePlayableUrl(fullTrack) ?: return@launch

                        val i = queue.indexOf(track)
                        if (i < 0) return@launch

                        val item = MediaItem.Builder()
                            .setUri(url)
                            .setMediaId(fullTrack.id.toString())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(fullTrack.title)
                                    .setArtist(fullTrack.user.username)
                                    .setArtworkUri(fullTrack.artworkUrl?.replace("-large", "-t500x500")?.let { Uri.parse(it) })
                                    .build()
                            )
                            .build()

                        controller?.replaceMediaItem(i, item)

                        if (cachedPath == null && url != "pending") {
                            trackCache.cacheAudioFile(fullTrack, url)
                        }
                    }
                }
            }
        }
    }

    private fun resolveAndReplace(mediaId: Long) {
        scope.launch {
            val track = queue.find { it.id == mediaId } ?: return@launch
            val cachedPath = trackCache.getCachedFilePath(mediaId)
            val fullTrack = if (cachedPath != null) track
                else if (track.media == null) {
                    runCatching { trackRepository.getTrack(track.id) }.getOrNull() ?: track
                } else track
            val url = if (cachedPath != null) cachedPath
                else trackRepository.resolvePlayableUrl(fullTrack) ?: return@launch

            val idx = queue.indexOf(track)
            if (idx < 0) return@launch

            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaId(fullTrack.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(fullTrack.title)
                        .setArtist(fullTrack.user.username)
                        .setArtworkUri(fullTrack.artworkUrl?.replace("-large", "-t500x500")?.let { Uri.parse(it) })
                        .build()
                )
                .build()

            controller?.replaceMediaItem(idx, item)
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
    }

    fun play(track: Track) {
        _state.update { it.copy(loadingTrackId = track.id) }
        val idx = queue.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            queueIndex = idx
        } else {
            queue.add(track)
            queueIndex = queue.lastIndex
        }
        updateQueueState()
        doPlay(track)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        queue.clear()
        queue.addAll(tracks)
        queueIndex = startIndex.coerceIn(0, tracks.lastIndex)
        _state.update { it.copy(loadingTrackId = tracks[queueIndex].id) }
        updateQueueState()
        doPlay(tracks[queueIndex])
    }

    fun skipToNext() {
        controller?.let {
            val next = it.currentMediaItemIndex + 1
            if (next < it.mediaItemCount) {
                it.seekToNext()
            }
        }
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    private fun updateQueueState() {
        _state.update {
            it.copy(
                hasNext = queueIndex < queue.lastIndex,
                hasPrev = queueIndex > 0
            )
        }
    }

    private fun doPlay(track: Track) {
        scope.launch {
            val cachedPath = trackCache.getCachedFilePath(track.id)
            val fullTrack = if (cachedPath != null) {
                track
            } else if (track.media == null) {
                runCatching { trackRepository.getTrack(track.id) }.getOrNull() ?: track
            } else track
            val url = if (cachedPath != null) cachedPath
                else trackRepository.resolvePlayableUrl(fullTrack) ?: return@launch

            recentTracks.removeAll { it.id == fullTrack.id }
            recentTracks.add(fullTrack)
            if (recentTracks.size > 20) recentTracks.removeFirst()

            val allItems = mutableListOf<MediaItem>()
            for (i in queue.indices) {
                val t = queue[i]
                val isCurrent = i == queueIndex
                val isNext = i == queueIndex + 1
                val isNearby = kotlin.math.abs(i - queueIndex) <= 2

                if (isCurrent || isNearby) {
                    val nearbyCached = trackCache.getCachedFilePath(t.id)
                    val full = if (nearbyCached != null || t.media != null) t
                        else runCatching { trackRepository.getTrack(t.id) }.getOrNull() ?: t
                    val tUrl = if (isCurrent) url
                        else nearbyCached
                            ?: runCatching { trackRepository.resolvePlayableUrl(full) }.getOrNull()

                    if (tUrl != null) {
                        allItems.add(MediaItem.Builder()
                            .setUri(tUrl)
                            .setMediaId(full.id.toString())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(full.title)
                                    .setArtist(full.user.username)
                                    .setArtworkUri(full.artworkUrl?.replace("-large", "-t500x500")?.let { android.net.Uri.parse(it) })
                                    .build()
                            ).build())
                    }
                } else {
                    allItems.add(MediaItem.Builder()
                        .setMediaId(t.id.toString())
                        .setUri("pending")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(t.title)
                                .setArtist(t.user.username)
                                .setArtworkUri(t.artworkUrl?.replace("-large", "-t500x500")?.let { android.net.Uri.parse(it) })
                                .build()
                        ).build())
                }
            }

            _state.value = _state.value.copy(currentTrack = fullTrack, durationMs = fullTrack.durationMs)
            extractSeedColor(fullTrack.artworkUrl)
            controller?.apply {
                setMediaItems(allItems, queueIndex, 0L)
                prepare()
                play()
            }

            if (cachedPath == null && url != "pending") {
                trackCache.cacheAudioFile(fullTrack, url)
            }
            trackCache.prefetchNearby(queue, queueIndex, scope)
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0L

    fun release() {
        stopPositionPolling()
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
            queue.forEach { queueJson.put(trackToJson(it)) }
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
                    queue.add(jsonToTrack(arr.getJSONObject(i)))
                }
                queueIndex = queueIdx.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
            }
            updateQueueState()
            scope.launch {
                val cachedPath = trackCache.getCachedFilePath(track.id)
                val fullTrack = if (cachedPath != null) track
                    else if (track.media == null) {
                        runCatching { trackRepository.getTrack(track.id) }.getOrNull() ?: track
                    } else track
                val url = if (cachedPath != null) cachedPath
                    else trackRepository.resolvePlayableUrl(fullTrack) ?: return@launch
                val item = MediaItem.Builder()
                    .setUri(url)
                    .setMediaId(fullTrack.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(fullTrack.title)
                            .setArtist(fullTrack.user.username)
                            .setArtworkUri(fullTrack.artworkUrl?.replace("-large", "-t500x500")?.let { Uri.parse(it) })
                            .build()
                    )
                    .build()
            _state.value = _state.value.copy(currentTrack = fullTrack, durationMs = fullTrack.durationMs, loadingTrackId = null)
                extractSeedColor(fullTrack.artworkUrl)
                controller?.apply {
                    setMediaItem(item)
                    prepare()
                    if (position > 0) seekTo(position)
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
