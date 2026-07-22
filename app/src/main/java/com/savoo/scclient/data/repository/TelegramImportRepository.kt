package com.savoo.scclient.data.repository

import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.local.TelegramImportDao
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.TelegramImportRecord
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.telegram.Id3Reader
import com.savoo.scclient.telegram.ImportItemResult
import com.savoo.scclient.telegram.ImportItemStatus
import com.savoo.scclient.telegram.ImportProgress
import com.savoo.scclient.telegram.TelegramAudioMessage
import com.savoo.scclient.telegram.TelegramClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * BETA: orchestrates a Telegram -> Resona library import. For every `messageAudio` in a chat's
 * history: resolve title/performer (from the message, falling back to ID3 tags on the file itself),
 * try to match it against SoundCloud search, and either add the match to favorites or fall back to
 * downloading the Telegram file straight into the existing offline-tracks store.
 */
@Singleton
class TelegramImportRepository @Inject constructor(
    private val telegramClient: TelegramClient,
    private val trackRepository: TrackRepository,
    private val favoritesDao: FavoritesDao,
    private val offlineTrackManager: OfflineTrackManager,
    private val telegramImportDao: TelegramImportDao,
) {
    /** Synthetic ids for offline-only imports live in a negative namespace - real SoundCloud ids are always positive. */
    private fun syntheticTrackId(chatId: Long, messageId: Long): Long =
        -(abs(chatId.hashCode().toLong() * 1_000_003L + messageId).coerceAtMost(Long.MAX_VALUE - 1) + 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun importChat(chatId: Long): Flow<ImportProgress> = flow {
        val alreadyImported = telegramImportDao.importedMessageIds(chatId).toHashSet()
        var matched = 0
        var downloaded = 0
        var failed = 0
        var skipped = 0
        var scanned = 0
        val recent = ArrayDeque<ImportItemResult>(RECENT_RESULTS_LIMIT)

        // Search + download per track is the bottleneck, not the chat-history walk, so fan out a
        // few tracks at once instead of processing strictly one at a time. Counter updates below
        // stay single-threaded: flatMapMerge runs producers concurrently but delivers results to
        // this collector sequentially.
        telegramClient.getAudioMessages(chatId)
            .flatMapMerge(concurrency = IMPORT_CONCURRENCY) { message ->
                if (message.messageId in alreadyImported) {
                    flowOf<Pair<TelegramAudioMessage, ImportItemResult?>>(message to null)
                } else {
                    flow { emit(message to importOne(message)) }
                }
            }
            .collect { (message, result) ->
                scanned++
                if (result == null) {
                    skipped++
                    emit(ImportProgress(scanned, matched, downloaded, failed, skipped, message.title, recent.toList()))
                    return@collect
                }
                telegramImportDao.upsert(
                    TelegramImportRecord(
                        chatId = message.chatId,
                        messageId = message.messageId,
                        title = result.resolvedTitle,
                        performer = result.resolvedPerformer,
                        status = result.status.name,
                        matchedTrackId = result.matchedTrackId,
                        reason = result.reason,
                    )
                )
                when (result.status) {
                    ImportItemStatus.MATCHED -> matched++
                    ImportItemStatus.DOWNLOADED_OFFLINE -> downloaded++
                    ImportItemStatus.FAILED -> failed++
                    ImportItemStatus.SKIPPED_DUPLICATE -> skipped++
                }
                if (recent.size >= RECENT_RESULTS_LIMIT) recent.removeLast()
                recent.addFirst(result)
                emit(ImportProgress(scanned, matched, downloaded, failed, skipped, result.resolvedTitle, recent.toList()))
            }
    }

    private suspend fun importOne(message: TelegramAudioMessage): ImportItemResult = withContext(Dispatchers.IO) {
        // A single stuck download or search call (TDLib edge cases, flaky network) used to be able to
        // wedge one of the flatMapMerge slots forever, which on a large chat gradually starved the
        // whole import down to zero throughput. Nothing here is allowed to run longer than this.
        withTimeoutOrNull(TRACK_TIMEOUT_MS) { importOneInner(message) }
            ?: ImportItemResult(message, message.title ?: "Untitled", message.performer, ImportItemStatus.FAILED, reason = "timed out")
    }

    private suspend fun importOneInner(message: TelegramAudioMessage): ImportItemResult {
        var title = message.title
        var performer = message.performer

        if (title.isNullOrBlank() || performer.isNullOrBlank()) {
            // Only need enough of the file to read its ID3 header - pulling the whole track just
            // to check for a title/performer is wasteful, especially before we even know whether
            // a SoundCloud match will make the download unnecessary entirely.
            val prefixFile = downloadMessageFile(message, limitBytes = ID3_PREFIX_BYTES)
            if (prefixFile != null) {
                val tags = Id3Reader.read(prefixFile)
                if (title.isNullOrBlank()) title = tags.title
                if (performer.isNullOrBlank()) performer = tags.performer
            }
        }
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: "Untitled"

        val match = runCatching { findBestMatch(resolvedTitle, performer, message.durationSec) }.getOrNull()
        if (match != null) {
            favoritesDao.addTrack(
                FavoriteTrack(
                    trackId = match.id,
                    title = match.title,
                    username = match.user.username,
                    artworkUrl = match.artworkUrl,
                    durationMs = match.durationMs,
                    permalinkUrl = match.permalinkUrl,
                    userId = match.user.id,
                    userAvatarUrl = match.user.avatarUrl,
                )
            )
            return ImportItemResult(message, resolvedTitle, performer, ImportItemStatus.MATCHED, match.id)
        }

        val file = downloadMessageFile(message)
        if (file == null) {
            return ImportItemResult(message, resolvedTitle, performer, ImportItemStatus.FAILED, reason = "download failed")
        }
        val syntheticId = syntheticTrackId(message.chatId, message.messageId)
        val saved = offlineTrackManager.adoptExternalFile(
            trackId = syntheticId,
            title = resolvedTitle,
            username = performer ?: "Telegram",
            durationMs = message.durationSec * 1000L,
            sourceFile = file,
        )
        return if (saved) {
            ImportItemResult(message, resolvedTitle, performer, ImportItemStatus.DOWNLOADED_OFFLINE, syntheticId)
        } else {
            ImportItemResult(message, resolvedTitle, performer, ImportItemStatus.FAILED, reason = "could not save offline copy")
        }
    }

    private suspend fun downloadMessageFile(message: TelegramAudioMessage, limitBytes: Long = 0L): File? {
        var last: File? = null
        telegramClient.downloadFile(message.fileId, message.fileSizeBytes, limitBytes).collect { (_, file) ->
            if (file != null) last = file
        }
        return last
    }

    private suspend fun findBestMatch(title: String, performer: String?, durationSec: Int): Track? {
        val query = listOfNotNull(performer, title).joinToString(" ")
        val candidates = runCatching { trackRepository.searchTracks(query) }.getOrElse { emptyList() }
        return candidates
            .map { it to matchScore(it, title, performer, durationSec) }
            .filter { it.second >= MATCH_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun matchScore(candidate: Track, title: String, performer: String?, durationSec: Int): Double {
        val titleScore = tokenOverlap(normalize(title), normalize(candidate.title))
        val performerScore = performer?.let { tokenOverlap(normalize(it), normalize(candidate.user.username)) } ?: 0.5
        val candidateDurationSec = candidate.durationMs / 1000
        val durationScore = if (durationSec <= 0 || candidateDurationSec <= 0) 0.5
            else (1.0 - (abs(candidateDurationSec - durationSec).toDouble() / durationSec).coerceAtMost(1.0))
        return titleScore * 0.55 + performerScore * 0.25 + durationScore * 0.2
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), " ") // drop "(official video)" style noise
            .replace(Regex("""[^a-z0-9а-яё\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun tokenOverlap(a: String, b: String): Double {
        val setA = a.split(" ").filter { it.isNotBlank() }.toSet()
        val setB = b.split(" ").filter { it.isNotBlank() }.toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val intersection = setA.intersect(setB).size
        return intersection.toDouble() / maxOf(setA.size, setB.size)
    }

    companion object {
        private const val MATCH_THRESHOLD = 0.55
        private const val IMPORT_CONCURRENCY = 4
        private const val ID3_PREFIX_BYTES = 256L * 1024L
        private const val RECENT_RESULTS_LIMIT = 6
        private const val TRACK_TIMEOUT_MS = 45_000L
    }
}
