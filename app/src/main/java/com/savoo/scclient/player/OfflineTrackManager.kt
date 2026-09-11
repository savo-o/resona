package com.savoo.scclient.player

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.savoo.scclient.data.local.OfflineDao
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.restrictionReason
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.debug.DebugLog
import com.savoo.scclient.di.PlainHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val BULK_DONE_VISIBLE_MS = 3000L

data class BulkDownloadProgress(
    val total: Int,
    val completed: Int = 0,
    val failed: Int = 0,
    val currentTitle: String? = null,
    val currentFraction: Float = 0f,
) {
    val isFinished: Boolean get() = completed >= total
    val fraction: Float get() = if (total <= 0) 1f else ((completed + currentFraction) / total).coerceIn(0f, 1f)
}

@Singleton
class OfflineTrackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineDao: OfflineDao,
    private val trackRepository: TrackRepository,
    @PlainHttpClient private val httpClient: OkHttpClient,
) {
    private val offlineDir: File by lazy {
        File(context.filesDir, "offline").apply { mkdirs() }
    }

    private val localFolderPrefs = context.getSharedPreferences("offline_local_folders", Context.MODE_PRIVATE)
    private val watchedFolderUrisKey = "watched_folder_uris"

    private val artworkRepairDone = AtomicBoolean(false)
    private val repairScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadingTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    val downloadingTrackIds = _downloadingTrackIds.asStateFlow()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bulkLock = Any()
    private val bulkQueue = ArrayDeque<Track>()
    private var bulkWorkerRunning = false
    private var bulkJob: Job? = null

    private val _bulkDownload = MutableStateFlow<BulkDownloadProgress?>(null)
    val bulkDownload = _bulkDownload.asStateFlow()

    fun enqueueDownloads(tracks: List<Track>) {
        synchronized(bulkLock) {
            val known = bulkQueue.mapTo(HashSet()) { it.id } + _downloadingTrackIds.value
            val fresh = tracks.distinctBy { it.id }.filter { it.id !in known }
            if (fresh.isEmpty()) return
            bulkQueue.addAll(fresh)
            if (bulkWorkerRunning) {
                _bulkDownload.update { it?.copy(total = it.total + fresh.size) }
            } else {
                bulkJob?.cancel()
                _bulkDownload.value = BulkDownloadProgress(total = fresh.size)
                bulkWorkerRunning = true
                bulkJob = downloadScope.launch { runBulkQueue() }
            }
        }
    }

    fun cancelBulkDownloads() {
        synchronized(bulkLock) {
            bulkQueue.clear()
            bulkJob?.cancel()
            bulkJob = null
            bulkWorkerRunning = false
            _bulkDownload.value = null
        }
    }

    private suspend fun runBulkQueue() {
        val job = currentCoroutineContext().job
        while (true) {
            val next = synchronized(bulkLock) {
                if (!job.isActive) return
                val track = bulkQueue.removeFirstOrNull()
                if (track == null) {
                    bulkWorkerRunning = false
                } else {
                    _bulkDownload.update { it?.copy(currentTitle = track.title, currentFraction = 0f) }
                }
                track
            } ?: break
            val result = saveForOffline(next) { fraction ->
                if (job.isActive) _bulkDownload.update { it?.copy(currentFraction = fraction) }
            }
            synchronized(bulkLock) {
                if (job.isActive) {
                    _bulkDownload.update {
                        it?.copy(
                            completed = it.completed + 1,
                            failed = it.failed + if (result.isFailure) 1 else 0,
                            currentFraction = 0f,
                        )
                    }
                }
            }
        }
        delay(BULK_DONE_VISIBLE_MS)
        synchronized(bulkLock) {
            if (!bulkWorkerRunning && job.isActive) _bulkDownload.value = null
        }
    }

    fun isOfflineTrack(trackId: Long): Flow<Boolean> = offlineDao.isOfflineTrack(trackId)

    suspend fun isOfflineTrackSync(trackId: Long): Boolean = offlineDao.isOfflineTrackSync(trackId)

    suspend fun getOfflineTrack(trackId: Long): OfflineTrack? = offlineDao.getOfflineTrack(trackId)

    fun getAllOfflineTracks(): Flow<List<OfflineTrack>> = offlineDao.getAllOfflineTracks()
        .onStart { if (artworkRepairDone.compareAndSet(false, true)) repairScope.launch { repairArtwork() } }

    suspend fun getTotalSize(): Long = offlineDao.getTotalSize() ?: 0L

    suspend fun getTrackCount(): Int = offlineDao.getTrackCount()

    suspend fun saveForOffline(track: Track, onProgress: ((Float) -> Unit)? = null): Result<Unit> = withContext(Dispatchers.IO) {
        _downloadingTrackIds.update { it + track.id }
        try {
            val audioFile = File(offlineDir, "${track.id}.mp3")
            if (audioFile.exists() && audioFile.length() > 0 && looksLikeAudioFile(audioFile)) {
                val artwork = offlineArtworkUri(track.id)
                    ?: downloadArtwork(track.id, track.artworkUrl)
                    ?: extractEmbeddedArtwork(track.id, audioFile)
                offlineDao.saveTrack(
                    OfflineTrack(
                        trackId = track.id,
                        title = track.title,
                        username = track.user.username,
                        artworkUrl = artwork ?: track.artworkUrl,
                        durationMs = track.durationMs,
                        permalinkUrl = track.permalinkUrl,
                        userId = track.user.id,
                        userAvatarUrl = track.user.avatarUrl,
                        localPath = audioFile.absolutePath,
                        fileSizeBytes = audioFile.length(),
                        genre = track.genre,
                    )
                )
                return@withContext Result.success(Unit)
            }
            audioFile.delete()

            val fullTrack = if (track.media == null) {
                runCatching { trackRepository.getTrack(track.id) }.getOrNull() ?: track
            } else track

            fullTrack.restrictionReason()?.let { reason ->
                DebugLog.log("OfflineTrack", "Refusing to save ${track.title}: restricted ($reason)")
                return@withContext Result.failure(Exception("Track is restricted by SoundCloud ($reason)"))
            }

            val stream = trackRepository.resolvePlayableStream(fullTrack)
                ?: return@withContext Result.failure(Exception("Cannot resolve track URL"))
            if (stream.isHls) return@withContext Result.failure(Exception("Track has no downloadable stream"))

            val tmpAudioFile = File(offlineDir, "${track.id}.mp3.tmp")
            val request = Request.Builder().url(stream.url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed: ${response.code}"))
                }
                response.body?.let { body ->
                    val totalBytes = body.contentLength()
                    body.byteStream().use { input ->
                        tmpAudioFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copied = 0L
                            var lastPercent = -1
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (onProgress != null && totalBytes > 0) {
                                    val percent = (copied * 100 / totalBytes).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent / 100f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (tmpAudioFile.length() <= 0 || !looksLikeAudioFile(tmpAudioFile) || !tmpAudioFile.renameTo(audioFile)) {
                tmpAudioFile.delete()
                return@withContext Result.failure(Exception("Downloaded file is empty or invalid"))
            }

            val artwork = downloadArtwork(track.id, track.artworkUrl)
                ?: extractEmbeddedArtwork(track.id, audioFile)

            offlineDao.saveTrack(
                OfflineTrack(
                    trackId = track.id,
                    title = track.title,
                    username = track.user.username,
                    artworkUrl = artwork ?: track.artworkUrl,
                    durationMs = track.durationMs,
                    permalinkUrl = track.permalinkUrl,
                    userId = track.user.id,
                    userAvatarUrl = track.user.avatarUrl,
                    localPath = audioFile.absolutePath,
                    fileSizeBytes = audioFile.length(),
                    genre = track.genre,
                )
            )
            DebugLog.log("OfflineTrack", "Saved ${track.title} (${audioFile.length()} bytes)")
            Result.success(Unit)
        } catch (e: Exception) {
            DebugLog.log("OfflineTrack", "Failed to save: ${e.message}")
            File(offlineDir, "${track.id}.mp3.tmp").delete()
            Result.failure(e)
        } finally {
            _downloadingTrackIds.update { it - track.id }
        }
    }

    suspend fun removeFromOffline(trackId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val track = offlineDao.getOfflineTrack(trackId)
            if (track != null) {
                val audioFile = File(track.localPath)
                if (audioFile.exists()) audioFile.delete()
                val artworkFile = File(offlineDir, "${trackId}.jpg")
                if (artworkFile.exists()) artworkFile.delete()
                offlineDao.removeTrack(trackId)
                DebugLog.log("OfflineTrack", "Removed offline track: ${track.title}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllOffline(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val tracks = offlineDao.getAllOfflineTracksSync()
            tracks.forEach { track ->
                val audioFile = File(track.localPath)
                if (audioFile.exists()) audioFile.delete()
                val artworkFile = File(offlineDir, "${track.trackId}.jpg")
                if (artworkFile.exists()) artworkFile.delete()
            }
            offlineDao.clearAll()
            DebugLog.log("OfflineTrack", "Cleared all offline tracks")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLocalPath(trackId: Long): String? {
        val offlineTrack = offlineDao.getOfflineTrack(trackId)
        if (offlineTrack == null) {
            DebugLog.log("OfflineTrack", "getLocalPath($trackId): not in DB")
            return null
        }
        val file = File(offlineTrack.localPath)
        val exists = file.exists()
        val size = if (exists) file.length() else 0L
        DebugLog.log("OfflineTrack", "getLocalPath($trackId): exists=$exists, size=$size, path=${offlineTrack.localPath}")
        return if (exists && size > 0) file.absolutePath else null
    }

    private fun offlineArtworkUri(trackId: Long): String? {
        val file = File(offlineDir, "$trackId.jpg")
        return if (file.exists() && file.length() > 0) "file://${file.absolutePath}" else null
    }

    private fun extractEmbeddedArtwork(trackId: Long, audioFile: File): String? {
        if (!audioFile.exists() || audioFile.length() <= 0) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFile.absolutePath)
            val picture = retriever.embeddedPicture ?: return null
            File(offlineDir, "$trackId.jpg").writeBytes(picture)
            offlineArtworkUri(trackId)
        } catch (e: Exception) {
            DebugLog.log("OfflineTrack", "extractEmbeddedArtwork failed for $trackId: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun downloadArtwork(trackId: Long, remoteUrl: String?): String? {
        val url = remoteUrl?.takeIf { it.startsWith("http") }?.replace("-large", "-t500x500") ?: return null
        return try {
            var ok = false
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val stream = response.body?.byteStream()
                if (response.isSuccessful && stream != null) {
                    File(offlineDir, "$trackId.jpg").outputStream().use { output -> stream.copyTo(output) }
                    ok = true
                }
            }
            if (ok) offlineArtworkUri(trackId) else null
        } catch (e: Exception) {
            DebugLog.log("OfflineTrack", "downloadArtwork failed for $trackId: ${e.message}")
            null
        }
    }

    private suspend fun repairArtwork() = withContext(Dispatchers.IO) {
        offlineDao.getAllOfflineTracksSync().forEach { offline ->
            val current = offline.artworkUrl
            val currentFile = current?.takeIf { it.startsWith("file://") }?.let { File(it.removePrefix("file://")) }
            if (currentFile != null && currentFile.exists() && currentFile.length() > 0) return@forEach
            val resolved = offlineArtworkUri(offline.trackId)
                ?: extractEmbeddedArtwork(offline.trackId, File(offline.localPath))
                ?: downloadArtwork(offline.trackId, current)
            DebugLog.log("OfflineArt", "id=${offline.trackId} artwork ${if (resolved != null) "restored" else "not found"}")
            if (resolved != null && resolved != current) {
                offlineDao.saveTrack(offline.copy(artworkUrl = resolved))
            }
        }
    }

    /**
     * Registers an already-downloaded file (e.g. from the Telegram import) as an offline track,
     * under a caller-supplied id rather than a resolved SoundCloud one. Copies the file into the
     * regular offline store so playback/cache lookups by id work exactly like any other offline track.
     */
    suspend fun adoptExternalFile(
        trackId: Long,
        title: String,
        username: String,
        durationMs: Long,
        sourceFile: File,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(offlineDir, "$trackId.mp3")
            sourceFile.copyTo(audioFile, overwrite = true)
            if (sourceFile != audioFile) sourceFile.delete()
            if (audioFile.length() <= 0) {
                audioFile.delete()
                return@withContext false
            }
            offlineDao.saveTrack(
                OfflineTrack(
                    trackId = trackId,
                    title = title,
                    username = username,
                    artworkUrl = extractEmbeddedArtwork(trackId, audioFile),
                    durationMs = durationMs,
                    permalinkUrl = null,
                    userId = 0,
                    userAvatarUrl = null,
                    localPath = audioFile.absolutePath,
                    fileSizeBytes = audioFile.length(),
                )
            )
            true
        } catch (e: Exception) {
            DebugLog.log("OfflineTrack", "adoptExternalFile failed: ${e.message}")
            false
        }
    }

    data class LocalImportResult(val imported: Int, val skipped: Int)

    private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma")

    suspend fun importLocalFolder(treeUri: Uri): LocalImportResult = withContext(Dispatchers.IO) {
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
        if (persisted) addWatchedFolder(treeUri)
        scanFolder(treeUri)
    }

    suspend fun refreshWatchedFolders(): LocalImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var skipped = 0
        for (treeUri in getWatchedFolders()) {
            val result = scanFolder(treeUri)
            imported += result.imported
            skipped += result.skipped
        }
        LocalImportResult(imported, skipped)
    }

    fun getWatchedFolders(): List<Uri> =
        localFolderPrefs.getStringSet(watchedFolderUrisKey, emptySet())
            .orEmpty()
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }

    fun watchedFolderDisplayName(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return treeUri.toString()
        return docId.substringAfterLast(':').substringAfterLast('/').ifBlank { docId }
    }

    suspend fun removeWatchedFolder(treeUri: Uri) = withContext(Dispatchers.IO) {
        val current = localFolderPrefs.getStringSet(watchedFolderUrisKey, emptySet()).orEmpty()
        localFolderPrefs.edit().putStringSet(watchedFolderUrisKey, current - treeUri.toString()).apply()
        runCatching {
            context.contentResolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        offlineDao.getOfflineTracksBySourceFolder(treeUri.toString()).forEach { track ->
            removeFromOffline(track.trackId)
        }
    }

    private fun addWatchedFolder(treeUri: Uri) {
        val current = localFolderPrefs.getStringSet(watchedFolderUrisKey, emptySet()).orEmpty()
        localFolderPrefs.edit().putStringSet(watchedFolderUrisKey, current + treeUri.toString()).apply()
    }

    private suspend fun scanFolder(treeUri: Uri): LocalImportResult {
        var imported = 0
        var skipped = 0
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx).orEmpty()
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val looksAudio = mime.startsWith("audio/") || ext in audioExtensions
                    if (!looksAudio) continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIdx))
                    if (syntheticLocalId(docUri.toString()).let { offlineDao.getOfflineTrack(it) != null }) continue
                    if (importLocalFile(docUri, name, treeUri)) imported++ else skipped++
                }
            }
        } catch (e: Exception) {
            DebugLog.log("OfflineTrack", "scanFolder failed: ${e.message}")
        }
        return LocalImportResult(imported, skipped)
    }

    private suspend fun importLocalFile(uri: Uri, displayName: String, sourceFolderUri: Uri): Boolean {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.')
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Unknown"
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val embeddedArt = runCatching { retriever.embeddedPicture }.getOrNull()
            retriever.release()

            val trackId = syntheticLocalId(uri.toString())
            val rawExt = displayName.substringAfterLast('.', "mp3").lowercase()
            val ext = rawExt.takeIf { it in audioExtensions } ?: "mp3"
            val audioFile = File(offlineDir, "$trackId.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                audioFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            if (audioFile.length() <= 0) {
                audioFile.delete()
                return false
            }

            var artworkUrl: String? = null
            if (embeddedArt != null) {
                val artFile = File(offlineDir, "$trackId.jpg")
                artFile.writeBytes(embeddedArt)
                artworkUrl = "file://${artFile.absolutePath}"
            }

            offlineDao.saveTrack(
                OfflineTrack(
                    trackId = trackId,
                    title = title,
                    username = artist,
                    artworkUrl = artworkUrl,
                    durationMs = durationMs,
                    permalinkUrl = null,
                    userId = 0,
                    userAvatarUrl = null,
                    localPath = audioFile.absolutePath,
                    fileSizeBytes = audioFile.length(),
                    sourceFolderUri = sourceFolderUri.toString(),
                )
            )
            true
        } catch (e: Exception) {
            DebugLog.log("OfflineTrack", "importLocalFile failed for $displayName: ${e.message}")
            false
        }
    }

    /** Distinct negative namespace from Telegram's synthetic ids (see TelegramImportRepository) so the
     * two schemes can't collide - real SoundCloud ids are always positive. */
    private fun syntheticLocalId(key: String): Long =
        -(abs(key.hashCode().toLong()) % 1_000_000_000L) - 2_000_000_000L
}
