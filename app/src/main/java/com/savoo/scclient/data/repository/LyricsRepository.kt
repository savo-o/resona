package com.savoo.scclient.data.repository

import android.util.Base64
import com.savoo.scclient.data.local.LyricsCacheDao
import com.savoo.scclient.data.model.KugouLyricsCandidate
import com.savoo.scclient.data.model.LyricsCacheEntity
import com.savoo.scclient.data.model.LyricsLine
import com.savoo.scclient.data.model.LyricsResult
import com.savoo.scclient.data.model.LyricsSearchResult
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.remote.GeniusApi
import com.savoo.scclient.data.remote.KugouApi
import com.savoo.scclient.data.remote.LyricsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class LyricsRepository @Inject constructor(
    private val api: LyricsApi,
    private val kugouApi: KugouApi,
    private val geniusApi: GeniusApi,
    private val lyricsCacheDao: LyricsCacheDao,
    private val settingsRepository: SettingsRepository,
) {
    private val cache = mutableMapOf<Pair<Long, LyricsProvider>, LyricsResult>()
    private val lrcTagRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val maxDurationDriftSec = 3.0
    // Kugou/QQ Music-sourced LRC embeds credit info as real timed lines rather than [ti:]/[ar:] tags -
    // these aren't lyrics and would otherwise show up as the first few "lines" of the song.
    private val creditLineRegex = Regex(
        """(?i)^(lyrics\s*by|composed\s*by|arranged\s*by|produced\s*by|written\s*by|词|曲|编曲|制作人|出品|监制|混音|母带|作词|作曲)\s*[:：]"""
    )

    suspend fun getLyrics(track: Track): LyricsResult {
        val provider = settingsRepository.settings.first().lyricsProvider
        cache[track.id to provider]?.let { return it }

        val cachedEntity = withContext(Dispatchers.IO) { lyricsCacheDao.get(track.id, provider.name) }
        if (cachedEntity?.type == "SYNCED") {
            val result = cachedEntity.toResult()
            cache[track.id to provider] = result
            return result
        }

        val synced = fetchSyncedFrom(provider, track)
        if (synced != null) {
            cache[track.id to provider] = synced
            withContext(Dispatchers.IO) { lyricsCacheDao.upsert(synced.toEntity(track.id, provider)) }
            return synced
        }

        if (cachedEntity?.type == "PLAIN") {
            val result = cachedEntity.toResult()
            cache[track.id to provider] = result
            return result
        }

        val fallback = if (settingsRepository.settings.first().geniusFallbackEnabled) {
            fetchFromGenius(track)?.let { LyricsResult.Plain(it, "Genius") }
        } else {
            null
        }
        val result = fallback ?: LyricsResult.NotFound

        cache[track.id to provider] = result
        withContext(Dispatchers.IO) { lyricsCacheDao.upsert(result.toEntity(track.id, provider)) }
        return result
    }

    private fun LyricsResult.toEntity(trackId: Long, provider: LyricsProvider): LyricsCacheEntity = when (this) {
        is LyricsResult.Synced -> LyricsCacheEntity(trackId, provider.name, "SYNCED", toLrc(lines), null)
        is LyricsResult.Plain -> LyricsCacheEntity(trackId, provider.name, "PLAIN", text, source)
        LyricsResult.NotFound -> LyricsCacheEntity(trackId, provider.name, "NOT_FOUND", null, null)
    }

    private fun LyricsCacheEntity.toResult(): LyricsResult = when (type) {
        "SYNCED" -> content?.let { parseLrc(it) }?.takeIf { it.isNotEmpty() }?.let { LyricsResult.Synced(it) } ?: LyricsResult.NotFound
        "PLAIN" -> content?.takeIf { it.isNotBlank() }?.let { LyricsResult.Plain(it, source ?: "LRCLIB") } ?: LyricsResult.NotFound
        else -> LyricsResult.NotFound
    }

    private fun toLrc(lines: List<LyricsLine>): String = lines.joinToString("\n") { line ->
        val minutes = line.timeMs / 60000
        val seconds = (line.timeMs % 60000) / 1000
        val hundredths = (line.timeMs % 1000) / 10
        "[%02d:%02d.%02d]%s".format(minutes, seconds, hundredths, line.text)
    }

    private suspend fun fetchSyncedFrom(provider: LyricsProvider, track: Track): LyricsResult? {
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                when (provider) {
                    LyricsProvider.LRCLIB -> fetchFromLrcLib(track)
                    LyricsProvider.KUGOU -> fetchFromKugou(track)
                }
            }
        }
        return outcome.getOrNull()
    }

    private suspend fun fetchFromLrcLib(track: Track): LyricsResult? {
        val durationSec = track.durationMs / 1000.0
        fun withinTolerance(candidate: LyricsSearchResult) =
            abs((candidate.duration ?: 0.0) - durationSec) <= maxDurationDriftSec

        val info = analyzeTitle(track)
        var plainFallback: LyricsResult.Plain? = null

        for (artist in info.artistCandidates) {
            val candidates = api.search(
                trackName = info.cleanTitle,
                artistName = artist,
            ).filter { it.instrumental != true }

            candidates
                .filter { !it.syncedLyrics.isNullOrBlank() }
                .minByOrNull { abs((it.duration ?: 0.0) - durationSec) }
                ?.takeIf(::withinTolerance)
                ?.syncedLyrics
                ?.let { parseLrc(it) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { return LyricsResult.Synced(it) }

            if (plainFallback == null) {
                candidates
                    .filter { !it.plainLyrics.isNullOrBlank() }
                    .minByOrNull { abs((it.duration ?: 0.0) - durationSec) }
                    ?.takeIf(::withinTolerance)
                    ?.plainLyrics
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { plainFallback = LyricsResult.Plain(it, "LRCLIB") }
            }
        }
        return plainFallback
    }

    private suspend fun fetchFromKugou(track: Track): LyricsResult? {
        val info = analyzeTitle(track)
        val title = info.cleanTitle
        val durationSec = (track.durationMs / 1000.0)

        for (artist in info.artistCandidates) {
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
                ?: continue

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
                ?: continue

            val downloadUrl = HttpUrl.Builder()
                .scheme("https").host("krcs.kugou.com").addPathSegment("download")
                .addQueryParameter("ver", "1")
                .addQueryParameter("client", "mobi")
                .addQueryParameter("id", candidate.id!!)
                .addQueryParameter("accesskey", candidate.accesskey!!)
                .addQueryParameter("fmt", "lrc")
                .addQueryParameter("charset", "utf8")
                .build()
            val encoded = kugouApi.downloadLyrics(downloadUrl.toString()).content ?: continue
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            val selfIdLines = setOf("$title - $artist".lowercase(), "$artist - $title".lowercase())
            val lines = parseLrc(decoded).filterNot { it.text.lowercase() in selfIdLines }
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }
        return null
    }

    private suspend fun fetchFromGenius(track: Track): String? = withContext(Dispatchers.IO) {
        runCatching {
            val info = analyzeTitle(track)
            for (artist in info.artistCandidates) {
                val query = "$artist ${info.cleanTitle}"
                val searchBody = geniusApi.search(query).use { it.string() }
                val songUrl = findGeniusSongUrl(searchBody, artist) ?: continue
                val html = geniusApi.fetchPage(songUrl).use { it.string() }
                val lyrics = extractGeniusLyrics(html)
                if (!lyrics.isNullOrBlank()) return@runCatching lyrics
            }
            null
        }.getOrNull()
    }

    private fun findGeniusSongUrl(searchJson: String, artistName: String): String? {
        val sections = JSONObject(searchJson).getJSONObject("response").getJSONArray("sections")
        for (i in 0 until sections.length()) {
            val hits = sections.getJSONObject(i).optJSONArray("hits") ?: continue
            for (j in 0 until hits.length()) {
                val hit = hits.getJSONObject(j)
                if (hit.optString("type") != "song") continue
                val result = hit.getJSONObject("result")
                val hitArtist = result.optJSONObject("primary_artist")?.optString("name").orEmpty()
                if (hitArtist.isNotBlank() && !artistLooselyMatches(hitArtist, artistName)) continue
                val url = result.optString("url")
                if (url.isNotBlank()) return url
            }
        }
        return null
    }

    private fun artistLooselyMatches(a: String, b: String): Boolean {
        val an = a.lowercase().trim()
        val bn = b.lowercase().trim()
        return an.isNotBlank() && bn.isNotBlank() && (an.contains(bn) || bn.contains(an))
    }

    private fun extractGeniusLyrics(html: String): String? {
        val containers = Jsoup.parse(html).select("div[data-lyrics-container=true]")
        if (containers.isEmpty()) return null
        val text = containers.joinToString("\n\n") { geniusContainerText(it) }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return text.ifBlank { null }
    }

    private fun geniusContainerText(element: Element): String {
        val sb = StringBuilder()
        fun walk(node: Node) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> if (node.tagName() == "br") sb.append("\n") else node.childNodes().forEach(::walk)
                else -> Unit
            }
        }
        element.childNodes().forEach(::walk)
        return sb.toString()
    }

    private fun List<KugouLyricsCandidate>.firstWithinDurationTolerance(trackDurationMs: Long): KugouLyricsCandidate? =
        firstOrNull { it.duration == null || abs(it.duration - trackDurationMs) <= maxDurationDriftSec * 1000 }

    private data class TitleInfo(val cleanTitle: String, val artistCandidates: List<String>)

    private fun analyzeTitle(track: Track): TitleInfo {
        var working = track.title.trim()
        var titleArtist: String? = null

        val dashMatch = Regex("""^([^-–—]{2,60}?)\s+[-–—]\s+(.+)$""").find(working)
        if (dashMatch != null) {
            val (left, right) = dashMatch.destructured
            if (right.isNotBlank() && !left.contains(Regex("""(?i)feat\.?|ft\."""))) {
                titleArtist = left.trim()
                working = right
            }
        }

        val featMatch = Regex("""(?i)\b(?:feat|ft)\.?\s+([^()\[\]]+)""").find(working)
        val featArtist = featMatch?.groupValues?.get(1)?.trim()?.trim(')', ']')

        val cleaned = working
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), "")
            .replace(Regex("""(?i)\b(feat|ft)\.?.*$"""), "")
            .trim()
            .ifBlank { track.title.trim() }

        val candidates = linkedSetOf(track.user.username)
        titleArtist?.let { candidates += it }
        featArtist?.let { candidates += it }
        candidates.removeAll { it.isBlank() }

        return TitleInfo(cleaned, candidates.toList().ifEmpty { listOf(track.user.username) })
    }

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
