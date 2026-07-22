package com.savoo.scclient.data.repository

import com.savoo.scclient.data.model.LyricsLine
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.remote.LyricsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class LyricsRepository @Inject constructor(
    private val api: LyricsApi,
) {
    // Empty list means "looked it up, nothing found" - callers rely on that to distinguish it from still-loading (null upstream).
    private val cache = mutableMapOf<Long, List<LyricsLine>>()
    private val lrcLineRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]\s*(.*)""")

    suspend fun getSyncedLyrics(track: Track): List<LyricsLine> {
        cache[track.id]?.let { return it }

        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val durationSec = track.durationMs / 1000.0
                val candidates = api.search(
                    trackName = cleanTitle(track.title),
                    artistName = track.user.username,
                )
                candidates
                    .filter { !it.syncedLyrics.isNullOrBlank() && it.instrumental != true }
                    .minByOrNull { abs((it.duration ?: 0.0) - durationSec) }
                    ?.syncedLyrics
                    ?.let { parseLrc(it) }
                    ?: emptyList()
            }
        }
        return outcome.getOrNull()?.also { cache[track.id] = it } ?: emptyList()
    }

    private fun cleanTitle(title: String): String =
        title
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), "")
            .replace(Regex("""(?i)\b(feat|ft)\.?.*$"""), "")
            .trim()

    private fun parseLrc(lrc: String): List<LyricsLine> =
        lrc.lineSequence()
            .mapNotNull { line -> lrcLineRegex.find(line) }
            .map { match ->
                val (minStr, secStr, fracStr, text) = match.destructured
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.toLong()
                }
                val timeMs = (minStr.toLong() * 60 + secStr.toLong()) * 1000 + frac
                LyricsLine(timeMs, text.trim())
            }
            .filter { it.text.isNotEmpty() }
            .sortedBy { it.timeMs }
            .toList()
}
