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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
        private const val KEY_CACHE_FORMAT_VERSION = "cache_format_version"
        private const val CACHE_FORMAT_VERSION = 2
    }

    private val audioDir: File by lazy {
        File(context.cacheDir, "audio").apply { mkdirs() }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // cacheAudioFile() used to write straight to the final path with no locking, so a file that
        // exists and is non-empty wasn't a reliable signal that it's actually complete/uncorrupted -
        // concurrent writers could have interleaved into it. Entries cached before this fix can't be
        // told apart from good ones after the fact, so wipe the cache once and let it rebuild clean.
        if (prefs.getInt(KEY_CACHE_FORMAT_VERSION, 1) < CACHE_FORMAT_VERSION) {
            audioDir.listFiles()?.forEach { it.delete() }
            prefs.edit().clear().putInt(KEY_CACHE_FORMAT_VERSION, CACHE_FORMAT_VERSION).apply()
        }
    }

    // Guards concurrent cacheAudioFile() calls for the same track (e.g. background preload of an
    // adjacent track racing a user directly jumping to play it) - without this, two writers could
    // both open the final ".mp3" path at once and interleave writes into it. A reader elsewhere
    // (getCachedFilePath's exists()+length()>0 check) can't tell a corrupt half-written file from
    // a good one, so a caller could get handed silently-broken audio - this is what "track
    // sometimes doesn't resolve/play" traced back to.
    private val downloadLocks = ConcurrentHashMap<Long, Mutex>()

    fun getCachedFilePath(trackId: Long): String? {
        val file = File(audioDir, "$trackId.mp3")
        if (!file.exists() || file.length() <= 0) return null
        if (!looksLikeAudio(file)) {
            // Poisoned entry from before the HLS-fallback fix below: resolvePlayableUrl can hand back
            // an HLS manifest URL (or, rarely, an HTML/JSON error page) when a track has no progressive
            // transcoding, and that got downloaded byte-for-byte into this ".mp3" file. It "exists and
            // is non-empty" but ExoPlayer can never decode it, so the track silently never plays again.
            // Delete it here so the caller falls through to a fresh network resolution instead.
            file.delete()
            return null
        }
        return file.absolutePath
    }

    // A real MP3 starts with an ID3 tag or an MPEG frame sync; an HLS playlist starts with "#EXTM3U"
    // and an API error response is HTML/JSON - cheap enough to sniff the first few bytes and reject
    // anything that isn't audio rather than caching (and forever replaying) the wrong content.
    private fun looksLikeAudio(file: File): Boolean = try {
        file.inputStream().use { input ->
            val header = ByteArray(16)
            val read = input.read(header)
            if (read <= 0) {
                false
            } else {
                val prefix = String(header, 0, read, Charsets.US_ASCII).trimStart()
                !prefix.startsWith("#EXTM3U") && !prefix.startsWith("<") &&
                    !prefix.startsWith("{") && !prefix.startsWith("[")
            }
        }
    } catch (_: Exception) {
        false
    }

    suspend fun cacheAudioFile(track: Track, url: String) = withContext(Dispatchers.IO) {
        val lock = downloadLocks.getOrPut(track.id) { Mutex() }
        lock.withLock {
            val file = File(audioDir, "${track.id}.mp3")
            if (file.exists() && file.length() > 0) return@withLock

            // Download to a temp file and rename into place only once fully written, so no reader
            // can ever observe a partial file at the canonical path (rename is atomic on the same
            // volume - the file at `file` either doesn't exist yet or is complete, never half-written).
            val tmpFile = File(audioDir, "${track.id}.mp3.tmp")
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        tmpFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tmpFile.length() > 0 && looksLikeAudio(tmpFile) && tmpFile.renameTo(file)) {
                        cacheTrackInfo(track)
                        evictOld()
                    } else {
                        tmpFile.delete()
                    }
                } else {
                    response.close()
                }
            } catch (_: Exception) {
                tmpFile.delete()
            }
        }
        // Conditional remove: only drop the map entry if it's still the Mutex instance we used - a
        // concurrent caller may have already replaced it with a fresh one after this one was created
        // but before this cleanup ran, and removing that newer entry would break its own locking.
        downloadLocks.remove(track.id, lock)
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
