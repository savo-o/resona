package com.savoo.scclient.data.repository

import android.util.Base64
import com.savoo.scclient.data.model.KugouLyricsCandidate
import com.savoo.scclient.data.model.LyricsLine
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.remote.KugouApi
import com.savoo.scclient.data.remote.LyricsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class LyricsRepository @Inject constructor(
    private val api: LyricsApi,
    private val kugouApi: KugouApi,
    private val settingsRepository: SettingsRepository,
) {
    // Empty list means "looked it up, nothing found" - callers rely on that to distinguish it from still-loading (null upstream).
    private val cache = mutableMapOf<Pair<Long, LyricsProvider>, List<LyricsLine>>()
    private val lrcTagRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val maxDurationDriftSec = 3.0
    // Kugou/QQ Music-sourced LRC embeds credit info as real timed lines rather than [ti:]/[ar:] tags -
    // these aren't lyrics and would otherwise show up as the first few "lines" of the song.
    private val creditLineRegex = Regex(
        """(?i)^(lyrics\s*by|composed\s*by|arranged\s*by|produced\s*by|written\s*by|词|曲|编曲|制作人|出品|监制|混音|母带|作词|作曲)\s*[:：]"""
    )

    suspend fun getSyncedLyrics(track: Track): List<LyricsLine> {
        val provider = settingsRepository.settings.first().lyricsProvider
        cache[track.id to provider]?.let { return it }
        val lines = fetchFrom(provider, track)
        cache[track.id to provider] = lines
        return lines
    }

    private suspend fun fetchFrom(provider: LyricsProvider, track: Track): List<LyricsLine> {
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                when (provider) {
                    LyricsProvider.LRCLIB -> fetchFromLrcLib(track)
                    LyricsProvider.KUGOU -> fetchFromKugou(track)
                }
            }
        }
        return outcome.getOrNull().orEmpty()
    }

    private suspend fun fetchFromLrcLib(track: Track): List<LyricsLine> {
        val durationSec = track.durationMs / 1000.0
        val candidates = api.search(
            trackName = cleanTitle(track.title),
            artistName = track.user.username,
        )
        return candidates
            .filter { !it.syncedLyrics.isNullOrBlank() && it.instrumental != true }
            .minByOrNull { abs((it.duration ?: 0.0) - durationSec) }
            ?.takeIf { abs((it.duration ?: 0.0) - durationSec) <= maxDurationDriftSec }
            ?.syncedLyrics
            ?.let { parseLrc(it) }
            ?: emptyList()
    }

    private suspend fun fetchFromKugou(track: Track): List<LyricsLine> {
        val title = cleanTitle(track.title)
        val artist = track.user.username
        val durationSec = (track.durationMs / 1000.0)

        val searchUrl = HttpUrl.Builder()
            .scheme("https").host("songsearch.kugou.com").addPathSegment("song_search_v2")
            .addQueryParameter("keyword", "$artist $title")
            .addQueryParameter("page", "1")
            .addQueryParameter("pagesize", "10")
            .addQueryParameter("userid", "-1")
            .addQueryParameter("clientver", "")
            .addQueryParameter("platform", "WebFilter")
            .addQueryParameter("filter", "2")
            .addQueryParameter("iscorrection", "1")
            .addQueryParameter("privilege_filter", "0")
            .build()
        val songs = kugouApi.searchSong(searchUrl.toString()).data?.lists.orEmpty()
        val song = songs
            .filter { !it.fileHash.isNullOrBlank() }
            .minByOrNull { abs((it.duration ?: 0) - durationSec) }
            ?.takeIf { abs((it.duration ?: 0) - durationSec) <= maxDurationDriftSec }
            ?: return emptyList()

        val lyricsSearchUrl = HttpUrl.Builder()
            .scheme("https").host("krcs.kugou.com").addPathSegment("search")
            .addQueryParameter("ver", "1")
            .addQueryParameter("man", "yes")
            .addQueryParameter("client", "mobi")
            .addQueryParameter("keyword", "$artist - $title")
            .addQueryParameter("duration", track.durationMs.toString())
            .addQueryParameter("hash", song.fileHash!!)
            .build()
        val candidate = kugouApi.searchLyrics(lyricsSearchUrl.toString()).candidates
            .filter { !it.id.isNullOrBlank() && !it.accesskey.isNullOrBlank() }
            .firstWithinDurationTolerance(track.durationMs)
            ?: return emptyList()

        val downloadUrl = HttpUrl.Builder()
            .scheme("https").host("krcs.kugou.com").addPathSegment("download")
            .addQueryParameter("ver", "1")
            .addQueryParameter("client", "mobi")
            .addQueryParameter("id", candidate.id!!)
            .addQueryParameter("accesskey", candidate.accesskey!!)
            .addQueryParameter("fmt", "lrc")
            .addQueryParameter("charset", "utf8")
            .build()
        val encoded = kugouApi.downloadLyrics(downloadUrl.toString()).content ?: return emptyList()
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        val selfIdLines = setOf("$title - $artist".lowercase(), "$artist - $title".lowercase())
        return parseLrc(decoded).filterNot { it.text.lowercase() in selfIdLines }
    }

    private fun List<KugouLyricsCandidate>.firstWithinDurationTolerance(trackDurationMs: Long): KugouLyricsCandidate? =
        firstOrNull { it.duration == null || abs(it.duration - trackDurationMs) <= maxDurationDriftSec * 1000 }

    private fun cleanTitle(title: String): String =
        title
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), "")
            .replace(Regex("""(?i)\b(feat|ft)\.?.*$"""), "")
            .trim()

    private fun parseLrc(lrc: String): List<LyricsLine> =
        lrc.lineSequence()
            .flatMap { line ->
                val tags = lrcTagRegex.findAll(line).toList()
                if (tags.isEmpty()) return@flatMap emptySequence()
                val text = line.substring(tags.last().range.last + 1).trim()
                if (text.isEmpty() || creditLineRegex.containsMatchIn(text)) return@flatMap emptySequence()
                tags.asSequence().map { tag ->
                    val (minStr, secStr, fracStr) = tag.destructured
                    val frac = when (fracStr.length) {
                        0 -> 0L
                        1 -> fracStr.toLong() * 100
                        2 -> fracStr.toLong() * 10
                        else -> fracStr.toLong()
                    }
                    val timeMs = (minStr.toLong() * 60 + secStr.toLong()) * 1000 + frac
                    LyricsLine(timeMs, text)
                }
            }
            .sortedBy { it.timeMs }
            .toList()
}
