package com.savoo.scclient.data.repository

import com.savoo.scclient.data.local.LyricsCacheDao
import com.savoo.scclient.data.local.LyricsSyncDao
import com.savoo.scclient.data.model.LyricsCacheEntity
import com.savoo.scclient.data.model.LyricsLine
import com.savoo.scclient.data.model.LyricsResult
import com.savoo.scclient.data.model.LyricsSearchResult
import com.savoo.scclient.data.model.LyricsSource
import com.savoo.scclient.data.model.LyricsSync
import com.savoo.scclient.data.model.LyricsSyncEntity
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.remote.GeniusApi
import com.savoo.scclient.data.remote.LyricsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    private val geniusApi: GeniusApi,
    private val lyricsCacheDao: LyricsCacheDao,
    private val lyricsSyncDao: LyricsSyncDao,
    private val settingsRepository: SettingsRepository,
) {
    private val cache = mutableMapOf<Long, LyricsResult>()
    private val lrcTagRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val maxDurationDriftSec = 3.0
    private val titleOnlyDurationDriftSec = 2.0
    private val creditLineRegex = Regex(
        """(?i)^(lyrics\s*by|composed\s*by|arranged\s*by|produced\s*by|written\s*by)\s*[:：]"""
    )

    suspend fun forceLyrics(track: Track, source: LyricsSource): LyricsResult {
        val result = when (source) {
            LyricsSource.LRCLIB -> withContext(Dispatchers.IO) {
                runCatching { fetchFromLrcLib(track) }.getOrNull()
            } ?: LyricsResult.NotFound
            LyricsSource.GENIUS -> fetchFromGenius(track)?.let { LyricsResult.Plain(it, "Genius") } ?: LyricsResult.NotFound
        }
        currentCoroutineContext().ensureActive()
        if (result != LyricsResult.NotFound) store(track.id, result) else cache.remove(track.id)
        return result
    }

    suspend fun getLyrics(track: Track): LyricsResult {
        cache[track.id]?.let { return it }

        val cachedEntity = withContext(Dispatchers.IO) { lyricsCacheDao.get(track.id, PROVIDER) }
        val cachedSynced = cachedEntity?.takeIf { it.type == "SYNCED" }?.toResult() as? LyricsResult.Synced
        if (cachedSynced != null && cachedSynced.sourceDurationMs != null) {
            cache[track.id] = cachedSynced
            return cachedSynced
        }

        val outcome = fetchSynced(track)
        currentCoroutineContext().ensureActive()
        if (outcome is FetchOutcome.Found && outcome.result is LyricsResult.Synced) {
            store(track.id, outcome.result)
            return outcome.result
        }

        if (cachedSynced != null) {
            cache[track.id] = cachedSynced
            return cachedSynced
        }

        if (cachedEntity?.type == "PLAIN" && cachedEntity.source != "Genius") {
            val result = cachedEntity.toResult()
            cache[track.id] = result
            return result
        }

        if (outcome is FetchOutcome.Found) {
            store(track.id, outcome.result)
            return outcome.result
        }

        val fallback = if (settingsRepository.settings.first().geniusFallbackEnabled) {
            fetchFromGenius(track)?.let { LyricsResult.Plain(it, "Genius") }
        } else {
            null
        }
        currentCoroutineContext().ensureActive()
        val result = fallback ?: LyricsResult.NotFound

        if (outcome is FetchOutcome.Failed) return result
        store(track.id, result)
        return result
    }

    fun observeSync(trackId: Long): Flow<LyricsSyncEntity?> = lyricsSyncDao.observe(trackId, PROVIDER)

    suspend fun saveSync(
        track: Track,
        trackDurationMs: Long?,
        sync: LyricsSync,
        automatic: LyricsSync,
        lyrics: LyricsResult.Synced?,
    ) = withContext(Dispatchers.IO) {
        if (sync == automatic) {
            lyricsSyncDao.delete(track.id, PROVIDER)
        } else {
            lyricsSyncDao.upsert(
                LyricsSyncEntity(
                    trackId = track.id,
                    provider = PROVIDER,
                    offsetMs = sync.offsetMs,
                    driftMsPerMin = sync.driftMsPerMin,
                    title = track.title,
                    artist = track.user.username,
                    trackDurationMs = trackDurationMs,
                    lyricsDurationMs = lyrics?.sourceDurationMs,
                    lastLineMs = lyrics?.lines?.lastOrNull()?.timeMs,
                )
            )
        }
    }

    private suspend fun store(trackId: Long, result: LyricsResult) {
        cache[trackId] = result
        withContext(Dispatchers.IO) { lyricsCacheDao.upsert(result.toEntity(trackId)) }
    }

    private sealed interface FetchOutcome {
        data class Found(val result: LyricsResult) : FetchOutcome
        data object Missing : FetchOutcome
        data object Failed : FetchOutcome
    }

    private fun LyricsResult.toEntity(trackId: Long): LyricsCacheEntity = when (this) {
        is LyricsResult.Synced -> LyricsCacheEntity(trackId, PROVIDER, "SYNCED", toLrc(lines), null, sourceDurationMs)
        is LyricsResult.Plain -> LyricsCacheEntity(trackId, PROVIDER, "PLAIN", text, source)
        LyricsResult.NotFound -> LyricsCacheEntity(trackId, PROVIDER, "NOT_FOUND", null, null)
    }

    private fun LyricsCacheEntity.toResult(): LyricsResult = when (type) {
        "SYNCED" -> content?.let { parseLrc(it) }?.takeIf { it.isNotEmpty() }?.let { LyricsResult.Synced(it, sourceDurationMs) } ?: LyricsResult.NotFound
        "PLAIN" -> content?.takeIf { it.isNotBlank() }?.let { LyricsResult.Plain(it, source ?: "LRCLIB") } ?: LyricsResult.NotFound
        else -> LyricsResult.NotFound
    }

    private fun toLrc(lines: List<LyricsLine>): String = lines.joinToString("\n") { line ->
        val minutes = line.timeMs / 60000
        val seconds = (line.timeMs % 60000) / 1000
        val hundredths = (line.timeMs % 1000) / 10
        "[%02d:%02d.%02d]%s".format(minutes, seconds, hundredths, line.text)
    }

    private suspend fun fetchSynced(track: Track): FetchOutcome {
        val outcome = withContext(Dispatchers.IO) { runCatching { fetchFromLrcLib(track) } }
        outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return outcome.fold(
            onSuccess = { result -> result?.let { FetchOutcome.Found(it) } ?: FetchOutcome.Missing },
            onFailure = { FetchOutcome.Failed },
        )
    }

    private suspend fun fetchFromLrcLib(track: Track): LyricsResult? {
        val durationSec = track.durationMs / 1000.0
        fun withinTolerance(candidate: LyricsSearchResult) =
            abs((candidate.duration ?: 0.0) - durationSec) <= maxDurationDriftSec

        val info = analyzeTitle(track)
        var plainFallback: LyricsResult.Plain? = null
        val seenIds = HashSet<Long>()

        fun consider(candidates: List<LyricsSearchResult>): LyricsResult.Synced? {
            val usable = candidates.filter { it.instrumental != true && withinTolerance(it) }
            usable.forEach { it.id?.let(seenIds::add) }

            usable
                .filter { !it.syncedLyrics.isNullOrBlank() }
                .sortedBy { abs((it.duration ?: 0.0) - durationSec) }
                .firstNotNullOfOrNull { candidate ->
                    parseLrc(candidate.syncedLyrics!!).takeIf { it.isNotEmpty() }
                        ?.let { LyricsResult.Synced(it, candidate.duration?.let { seconds -> Math.round(seconds * 1000) }) }
                }
                ?.let { return it }

            if (plainFallback == null) {
                usable
                    .filter { !it.plainLyrics.isNullOrBlank() }
                    .minByOrNull { abs((it.duration ?: 0.0) - durationSec) }
                    ?.plainLyrics
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { plainFallback = LyricsResult.Plain(it, "LRCLIB") }
            }
            return null
        }

        for (artist in info.artistCandidates) {
            consider(api.search(trackName = info.cleanTitle, artistName = artist))?.let { return it }
        }

        for (artist in info.artistCandidates) {
            consider(api.searchQuery("$artist ${info.cleanTitle}"))?.let { return it }
        }

        val (byTitleAndArtist, byTitleOnly) = api.searchByTitle(info.cleanTitle)
            .filter { it.id == null || it.id !in seenIds }
            .partition { candidate -> info.artistCandidates.any { looselyMatches(candidate.artistName.orEmpty(), it) } }
        consider(byTitleAndArtist)?.let { return it }

        val sameSong = byTitleOnly.filter { candidate ->
            sameTitle(candidate.trackName.orEmpty(), info.cleanTitle) &&
                abs((candidate.duration ?: 0.0) - durationSec) <= titleOnlyDurationDriftSec
        }
        consider(sameSong)?.let { return it }

        return plainFallback
    }

    private suspend fun fetchFromGenius(track: Track): String? = withContext(Dispatchers.IO) {
        val outcome = runCatching {
            val info = analyzeTitle(track)
            for (artist in info.artistCandidates) {
                val query = "$artist ${info.cleanTitle}"
                val searchBody = geniusApi.search(query).use { it.string() }
                val songUrl = findGeniusSongUrl(searchBody, artist, info.cleanTitle) ?: continue
                val html = geniusApi.fetchPage(songUrl).use { it.string() }
                val lyrics = extractGeniusLyrics(html)
                if (!lyrics.isNullOrBlank()) return@runCatching lyrics
            }
            null
        }
        outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        outcome.getOrNull()
    }

    private fun findGeniusSongUrl(searchJson: String, artistName: String, title: String): String? {
        val sections = JSONObject(searchJson).getJSONObject("response").getJSONArray("sections")
        for (i in 0 until sections.length()) {
            val hits = sections.getJSONObject(i).optJSONArray("hits") ?: continue
            for (j in 0 until hits.length()) {
                val hit = hits.getJSONObject(j)
                if (hit.optString("type") != "song") continue
                val result = hit.getJSONObject("result")
                val hitArtist = result.optJSONObject("primary_artist")?.optString("name").orEmpty()
                if (hitArtist.isNotBlank() && !looselyMatches(hitArtist, artistName)) continue
                val hitTitle = result.optString("title")
                if (!looselyMatches(hitTitle, title)) continue
                val url = result.optString("url")
                if (url.isNotBlank()) return url
            }
        }
        return null
    }

    private fun looselyMatches(a: String, b: String): Boolean {
        val an = normalizeForMatch(a)
        val bn = normalizeForMatch(b)
        return an.isNotBlank() && bn.isNotBlank() && (an.contains(bn) || bn.contains(an))
    }

    private fun sameTitle(candidate: String, title: String): Boolean {
        val stripped = candidate.replace(Regex("""[(\[][^)\]]*[)\]]"""), "")
        val cn = normalizeForMatch(stripped)
        return cn.isNotBlank() && cn == normalizeForMatch(title)
    }

    private fun normalizeForMatch(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

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

    private data class TitleInfo(val cleanTitle: String, val artistCandidates: List<String>)

    private fun analyzeTitle(track: Track): TitleInfo {
        var working = track.title.trim().substringBefore('|').trim().ifBlank { track.title.trim() }
        var titleArtist: String? = null

        val dashMatch = Regex("""^([^-–—]{2,60}?)\s+[-–—]\s+(.+)$""").find(working)
        if (dashMatch != null) {
            val (left, right) = dashMatch.destructured
            val rightIsOnlyVariant = right.replace(Regex("""[(\[{][^)\]}]*[)\]}]"""), " ")
                .replace(variantRegex, " ")
                .replace(Regex("""[^\p{L}\p{N}]"""), "")
                .isBlank()
            if (rightIsOnlyVariant) {
                working = left
            } else if (right.isNotBlank() && !left.contains(Regex("""(?i)feat\.?|ft\."""))) {
                titleArtist = left.trim()
                working = right
            }
        }

        val featMatch = Regex("""(?i)\b(?:feat|ft)\.?\s+([^()\[\]]+)""").find(working)
        val featArtist = featMatch?.groupValues?.get(1)?.trim()?.trim(')', ']')
        val withMatch = Regex("""(?i)\s+(?:w/|with)\s*([^()\[\]]+)$""").find(working)
        val withArtist = withMatch?.groupValues?.get(1)?.trim()

        val cleaned = working
            .replace(Regex("""[(\[{][^)\]}]*[)\]}]"""), " ")
            .replace(Regex("""(?i)\b(feat|ft)\.?\s.*$"""), "")
            .replace(Regex("""(?i)\s+(w/|with\s).*$"""), "")
            .replace(Regex("""(?i)\s+prod\.?\s.*$"""), "")
            .replace(variantRegex, " ")
            .replace(Regex("""[^\p{L}\p{N}\s'’&.,!?-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('-', '–', '—', '+', '/', ',', ':', '.', ' ')
            .ifBlank { track.title.trim() }

        val candidates = linkedSetOf<String>()
        listOfNotNull(track.user.username, track.user.fullName, titleArtist, featArtist, withArtist).forEach { raw ->
            val name = raw.trim()
            candidates += name
            name.split(artistSplitRegex).map { it.trim() }.filter { it.length >= 2 }.forEach { candidates += it }
        }
        candidates.removeAll { it.isBlank() }

        return TitleInfo(cleaned, candidates.toList().ifEmpty { listOf(track.user.username) })
    }

    private val variantRegex = Regex(
        """(?i)\b((super|ultra)\s+)?(sped[\s-]*up|speed(ed)?[\s-]*up|spedup|speedup|slowed([\s-]*down)?|pitched([\s-]*(up|down))?|reverb|nightcore|daycore|8d(\s*audio)?|bass[\s-]*boost(ed)?)(\s*(version|ver\.?|edit|remix))?\b\.*|""" +
            """\b(""" +
            """tik[\s-]*tok(\s*version)?|extended(\s*(mix|version))?|radio\s*edit|remaster(ed)?|""" +
            """official\s*(music\s*)?(video|audio)|lyric(s)?\s*video|visuali[sz]er|free\s*(dl|download)|out\s*now|snippet|leak(ed)?)\b"""
    )

    private val artistSplitRegex = Regex("""(?i)\s+(?:x|&|and|w/|with|vs\.?|feat\.?|ft\.?)\s+|\s*[,+]\s*|\s+w/""")

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

    private companion object {
        const val PROVIDER = "LRCLIB"
    }
}
