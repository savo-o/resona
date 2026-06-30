package com.savoo.scclient.player

import android.content.Context
import android.content.SharedPreferences
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.di.PlainHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackRepository: TrackRepository,
    @PlainHttpClient private val httpClient: OkHttpClient,
) {
    companion object {
        private const val MAX_CACHED = 20
        private const val PREFS_NAME = "track_cache"
        private const val KEY_TRACK_ORDER = "track_order"
        private const val KEY_TRACK_INFO_PREFIX = "track_info_"
    }

    private val audioDir: File by lazy {
        File(context.cacheDir, "audio").apply { mkdirs() }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCachedFilePath(trackId: Long): String? {
        val file = File(audioDir, "$trackId.mp3")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    suspend fun cacheAudioFile(track: Track, url: String) = withContext(Dispatchers.IO) {
        val file = File(audioDir, "${track.id}.mp3")
        if (file.exists() && file.length() > 0) return@withContext

        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (file.length() > 0) {
                    cacheTrackInfo(track)
                    evictOld()
                } else {
                    file.delete()
                }
            } else {
                response.close()
            }
        } catch (_: Exception) {
            file.delete()
        }
    }

    fun cacheTrackInfo(track: Track) {
        val info = JSONObject().apply {
            put("id", track.id)
            put("title", track.title)
            put("durationMs", track.durationMs)
            put("artworkUrl", track.artworkUrl.orEmpty())
            put("permalinkUrl", track.permalinkUrl.orEmpty())
            put("username", track.user.username)
            put("userId", track.user.id)
            put("userAvatarUrl", track.user.avatarUrl.orEmpty())
            put("cachedAt", System.currentTimeMillis())
        }
        prefs.edit()
            .putString("$KEY_TRACK_INFO_PREFIX${track.id}", info.toString())
            .apply()
        updateTrackOrder(track.id)
    }

    private fun updateTrackOrder(trackId: Long) {
        val order = getTrackOrder().toMutableList()
        order.remove(trackId)
        order.add(0, trackId)
        prefs.edit().putString(KEY_TRACK_ORDER, order.joinToString(",")).apply()
    }

    private fun getTrackOrder(): List<Long> {
        val str = prefs.getString(KEY_TRACK_ORDER, null) ?: return emptyList()
        return str.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    private fun evictOld() {
        val order = getTrackOrder().toMutableList()
        while (order.size > MAX_CACHED) {
            val oldestId = order.removeLast()
            val file = File(audioDir, "$oldestId.mp3")
            if (file.exists()) file.delete()
            prefs.edit().remove("$KEY_TRACK_INFO_PREFIX$oldestId").apply()
        }
        prefs.edit().putString(KEY_TRACK_ORDER, order.joinToString(",")).apply()
    }

    fun prefetchNearby(tracks: List<Track>, currentIndex: Int, scope: CoroutineScope) {
        val indices = buildList {
            val nextIdx = currentIndex + 1
            if (nextIdx in tracks.indices) add(nextIdx)
            val nextNextIdx = currentIndex + 2
            if (nextNextIdx in tracks.indices) add(nextNextIdx)
            val prevIdx = currentIndex - 1
            if (prevIdx in tracks.indices) add(prevIdx)
        }
        for (idx in indices) {
            val track = tracks[idx]
            if (getCachedFilePath(track.id) != null) continue
            scope.launch {
                val fullTrack = if (track.media == null) {
                    runCatching { trackRepository.getTrack(track.id) }.getOrNull() ?: track
                } else track
                val url = runCatching { trackRepository.resolvePlayableUrl(fullTrack) }.getOrNull()
                    ?: return@launch
                cacheAudioFile(fullTrack, url)
            }
        }
    }
}
