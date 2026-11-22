package com.savoo.scclient.player

import com.savoo.scclient.compat.*
import kotlinx.coroutines.IO
import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.Uri
import com.savoo.scclient.data.local.OfflineDao
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.restrictionReason
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.debug.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import com.savoo.scclient.compat.http.OkHttpClient
import com.savoo.scclient.compat.http.Request
import com.savoo.scclient.compat.http.HttpException
import com.savoo.scclient.compat.File
import com.savoo.scclient.compat.AtomicBoolean
import kotlin.math.abs

private const val BULK_DONE_VISIBLE_MS = 3000L
const val BULK_PARALLEL_WORKERS = 3
private const val BULK_START_SPACING_MS = 700L
private const val BULK_RATE_LIMIT_BACKOFF_MS = 5000L

class RateLimitedException(message: String) : Exception(message)

data class ActiveBulkDownload(
    val title: String,
    val fraction: Float = 0f,
)

data class BulkDownloadProgress(
    val total: Int,
    val completed: Int = 0,
    val failed: Int = 0,
    val active: Map<Long, ActiveBulkDownload> = emptyMap(),
    val parallel: Boolean = false,
) {
    val isFinished: Boolean get() = completed >= total
    val currentTitle: String? get() = active.values.firstOrNull()?.title
    val inProgress: Int get() = (completed + active.size.coerceAtLeast(1)).coerceAtMost(total)
    val fraction: Float get() = if (total <= 0) 1f else ((completed + active.values.sumOf { it.fraction.toDouble() }.toFloat()) / total).coerceIn(0f, 1f)
}
class OfflineTrackManager(
    private val context: Context,
    private val offlineDao: OfflineDao,
    private val trackRepository: TrackRepository,
    private val httpClient: OkHttpClient,
    private val waveformRepository: com.savoo.scclient.data.repository.WaveformRepository,
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
    private var bulkWorkers = 0
    private var bulkParallel = false
    private var bulkRateLimited = false
    private var bulkNextStartAt = 0L
    private val bulkRetriedIds = HashSet<Long>()

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
                launchBulkWorkersLocked()
            } else {
                bulkJob?.cancel()
                bulkRateLimited = false
                bulkRetriedIds.clear()
                _bulkDownload.value = BulkDownloadProgress(total = fresh.size, parallel = bulkParallel)
                bulkWorkerRunning = true
                bulkWorkers = 0
                bulkJob = SupervisorJob(downloadScope.coroutineContext.job)
                launchBulkWorkersLocked()
            }
        }
    }

    fun setBulkParallel(enabled: Boolean) {
        synchronized(bulkLock) {
            bulkParallel = enabled
            bulkRateLimited = false
            _bulkDownload.update { it?.copy(parallel = enabled) }
            if (bulkWorkerRunning) launchBulkWorkersLocked()
        }
    }

    fun cancelBulkDownloads() {
        synchronized(bulkLock) {
            bulkQueue.clear()
            bulkJob?.cancel()
            bulkJob = null
            bulkWorkerRunning = false
            bulkWorkers = 0
            _bulkDownload.value = null
        }
    }

    private fun launchBulkWorkersLocked() {
        val job = bulkJob ?: return
        if (!job.isActive) return
        val limit = bulkWorkerLimitLocked()
        while (bulkWorkers < limit && bulkWorkers < bulkQueue.size) {
            bulkWorkers++
            CoroutineScope(job + Dispatchers.IO).launch { runBulkWorker(job) }
        }
    }

    private fun bulkWorkerLimitLocked(): Int =
        if (bulkParallel && !bulkRateLimited) BULK_PARALLEL_WORKERS else 1

    private suspend fun runBulkWorker(job: Job) {
        while (true) {
            var startDelayMs = 0L
            val next = synchronized(bulkLock) {
                if (!job.isActive) return
                val limit = bulkWorkerLimitLocked()
                val track = if (bulkWorkers > limit) null else bulkQueue.removeFirstOrNull()
                if (track == null) {
                    bulkWorkers--
                    if (bulkWorkers == 0 && bulkQueue.isEmpty()) {
                        bulkWorkerRunning = false
                        downloadScope.launch { hideFinishedBulk(job) }
                    }
                } else {
                    val now = currentTimeMillis()
                    val startAt = maxOf(now, bulkNextStartAt)
                    startDelayMs = startAt - now
                    bulkNextStartAt = startAt + BULK_START_SPACING_MS
                    _bulkDownload.update { it?.copy(active = it.active + (track.id to ActiveBulkDownload(track.title))) }
                }
                track
            } ?: return
            if (startDelayMs > 0) delay(startDelayMs)
            val result = saveForOffline(next) { fraction ->
                if (job.isActive) {
                    _bulkDownload.update { progress ->
                        val current = progress?.active?.get(next.id) ?: return@update progress
                        progress.copy(active = progress.active + (next.id to current.copy(fraction = fraction)))
                    }
                }
            }
            if (result.exceptionOrNull() is RateLimitedException) {
                val retry = synchronized(bulkLock) {
                    if (!job.isActive) return
                    bulkRateLimited = true
                    bulkNextStartAt = currentTimeMillis() + BULK_RATE_LIMIT_BACKOFF_MS
                    _bulkDownload.update { it?.copy(parallel = false, active = it.active - next.id) }
                    if (bulkRetriedIds.add(next.id)) {
                        bulkQueue.addFirst(next)
                        true
                    } else {
                        false
                    }
                }
                if (retry) continue
            }
            synchronized(bulkLock) {
                if (job.isActive) {
                    _bulkDownload.update {
                        it?.copy(
                            completed = it.completed + 1,
                            failed = it.failed + if (result.isFailure) 1 else 0,
                            active = it.active - next.id,
                        )
                    }
                }
            }
        }
    }

    private suspend fun hideFinishedBulk(job: Job) {
        delay(BULK_DONE_VISIBLE_MS)
        synchronized(bulkLock) {
            if (!bulkWorkerRunning && bulkJob === job && job.isActive) {
                _bulkDownload.value = null
                job.cancel()
            }
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
        while (true) {
            val active = _downloadingTrackIds.value
            if (track.id in active) return@withContext Result.failure(IllegalStateException("Track download already in progress"))
            if (_downloadingTrackIds.compareAndSet(active, active + track.id)) break
        }
        var downloadedAsset: File? = null
        try {
            offlineDao.getOfflineTrack(track.id)?.let { saved ->
                val file = offlineAudioFile(saved.localPath)
                if (file.isDirectory && file.exists()) return@withContext Result.success(Unit)
            }
            val audioFile = listOf("mp3", "m4a").map { File(offlineDir, "${track.id}.$it") }
                .firstOrNull { it.exists() } ?: File(offlineDir, "${track.id}.mp3")
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
                runCatching { trackRepository.getTrack(track.id) }
                    .onFailure { if (it.isRateLimit()) throw RateLimitedException("Rate limited by SoundCloud") }
                    .getOrNull() ?: track
            } else track

            fullTrack.restrictionReason()?.let { reason ->
                DebugLog.log("OfflineTrack", "Refusing to save ${track.title}: restricted ($reason)")
                return@withContext Result.failure(Exception("Track is restricted by SoundCloud ($reason)"))
            }

            val stream = runCatching { trackRepository.resolveDownloadStream(fullTrack) }
                .onFailure { if (it.isRateLimit()) throw RateLimitedException("Rate limited by SoundCloud") }
                .getOrThrow()
                ?: return@withContext Result.failure(Exception("Track has no downloadable stream"))

            val tmpAudioFile = File(offlineDir, "${track.id}.mp3.tmp")
            val targetFile: File
            if (stream.isHls) {
                targetFile = downloadHlsAsset(stream.url, track.title, onProgress)
                downloadedAsset = targetFile
                ensureActive()
            } else {
                targetFile = File(offlineDir, "${track.id}." + if (stream.isMp4) "m4a" else "mp3")
                tmpAudioFile.delete()
                val request = Request.Builder().url(stream.url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        return@withContext Result.failure(RateLimitedException("Download rate limited: 429"))
                    }
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Download failed: ${response.code}"))
                    }
                    response.body?.let { body ->
                        val totalBytes = body.contentLength()
                        body.byteStream().use { input ->
                            tmpAudioFile.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
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
                if (tmpAudioFile.length() <= 0 || !looksLikeAudioFile(tmpAudioFile)) {
                    throw IOException("Downloaded file is empty or invalid")
                }
                targetFile.delete()
                if (!tmpAudioFile.renameTo(targetFile)) throw IOException("Cannot save audio download")
            }

            val artwork = downloadArtwork(track.id, track.artworkUrl)
                ?: if (stream.isHls) null else extractEmbeddedArtwork(track.id, targetFile)

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
                    localPath = targetFile.absolutePath,
                    fileSizeBytes = if (targetFile.isDirectory) targetFile.walkBottomUp().filter { it.isFile }.sumOf { it.length() } else targetFile.length(),
                    genre = track.genre,
                )
            )
            downloadedAsset = null
            DebugLog.log("OfflineTrack", "Saved ${track.title} (${targetFile.length()} bytes)")
            repairScope.launch { runCatching { waveformRepository.getWaveform(track) } }
            Result.success(Unit)
        } catch (e: Exception) {
            DebugLog.log("OfflineTrack", "Failed to save: ${e.message}")
            File(offlineDir, "${track.id}.mp3.tmp").delete()
            downloadedAsset?.deleteRecursively()
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            _downloadingTrackIds.update { it - track.id }
        }
    }

    private fun Throwable.isRateLimit(): Boolean =
        this is RateLimitedException || (this is HttpException && code() == 429)

    suspend fun removeFromOffline(trackId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val track = offlineDao.getOfflineTrack(trackId)
            if (track != null) {
                val audioFile = offlineAudioFile(track.localPath)
                if (audioFile.exists()) audioFile.deleteRecursively()
                val artworkFile = File(offlineDir, "${trackId}.jpg")
                if (artworkFile.exists()) artworkFile.delete()
                File(offlineDir, "$trackId.wave").delete()
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
                val audioFile = offlineAudioFile(track.localPath)
                if (audioFile.exists()) audioFile.deleteRecursively()
                val artworkFile = File(offlineDir, "${track.trackId}.jpg")
                if (artworkFile.exists()) artworkFile.delete()
                File(offlineDir, "${track.trackId}.wave").delete()
            }
            offlineDao.clearAll()
            DebugLog.log("OfflineTrack", "Cleared all offline tracks")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun offlineAudioFile(path: String): File {
        File(path).takeIf { it.exists() }?.let { return it }
        val relativeLibraryPath = path.substringAfter("/Library/", "")
        if (relativeLibraryPath.isNotEmpty()) {
            val home = Platform.filesDirPath.substringBefore("/Library/")
            File("$home/Library/$relativeLibraryPath").takeIf { it.exists() }?.let { return it }
        }
        return File(offlineDir, path.substringAfterLast('/'))
    }

    suspend fun getLocalPath(trackId: Long): String? {
        val offlineTrack = offlineDao.getOfflineTrack(trackId)
        if (offlineTrack == null) {
            DebugLog.log("OfflineTrack", "getLocalPath($trackId): not in DB")
            return null
        }
        val file = offlineAudioFile(offlineTrack.localPath)
        val exists = file.exists()
        val size = if (exists) file.length() else 0L
        DebugLog.log("OfflineTrack", "getLocalPath($trackId): exists=$exists, size=$size, path=${offlineTrack.localPath}")
        return if (exists && (file.isDirectory || size > 0)) file.absolutePath else null
    }

    private fun offlineArtworkUri(trackId: Long): String? {
        val file = File(offlineDir, "$trackId.jpg")
        return if (file.exists() && file.length() > 0) "file://${file.absolutePath}" else null
    }

    private fun extractEmbeddedArtwork(trackId: Long, audioFile: File): String? = null

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

    fun watchedFolderDisplayName(treeUri: Uri): String = treeUri.lastPathSegment ?: treeUri.toString()

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

    private suspend fun scanFolder(treeUri: Uri): LocalImportResult = LocalImportResult(0, 0)
}
