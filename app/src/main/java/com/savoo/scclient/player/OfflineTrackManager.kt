package com.savoo.scclient.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.savoo.scclient.data.local.OfflineDao
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.debug.DebugLog
import com.savoo.scclient.di.PlainHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

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

    fun isOfflineTrack(trackId: Long): Flow<Boolean> = offlineDao.isOfflineTrack(trackId)

    suspend fun isOfflineTrackSync(trackId: Long): Boolean = offlineDao.isOfflineTrackSync(trackId)

    suspend fun getOfflineTrack(trackId: Long): OfflineTrack? = offlineDao.getOfflineTrack(trackId)

    fun getAllOfflineTracks(): Flow<List<OfflineTrack>> = offlineDao.getAllOfflineTracks()

    suspend fun getTotalSize(): Long = offlineDao.getTotalSize() ?: 0L

    suspend fun getTrackCount(): Int = offlineDao.getTrackCount()

    suspend fun saveForOffline(track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(offlineDir, "${track.id}.mp3")
            if (audioFile.exists() && audioFile.length() > 0) {
                offlineDao.saveTrack(
                    OfflineTrack(
                        trackId = track.id,
                        title = track.title,
                        username = track.user.username,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs,
                        permalinkUrl = track.permalinkUrl,
                        userId = track.user.id,
                        userAvatarUrl = track.user.avatarUrl,
                        localPath = audioFile.absolutePath,
                        fileSizeBytes = audioFile.length(),
                    )
                )
                return@withContext Result.success(Unit)
            }

            val fullTrack = if (track.media == null) {
                runCatching { trackRepository.getTrack(track.id) }.getOrNull() ?: track
            } else track

            val url = trackRepository.resolvePlayableUrl(fullTrack)
                ?: return@withContext Result.failure(Exception("Cannot resolve track URL"))

            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed: ${response.code}"))
                }
                response.body?.byteStream()?.use { input ->
                    audioFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val artworkFile = File(offlineDir, "${track.id}.jpg")
            track.artworkUrl?.replace("-large", "-t500x500")?.let { artUrl ->
                try {
                    val artRequest = Request.Builder().url(artUrl).build()
                    httpClient.newCall(artRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { input ->
                                artworkFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (audioFile.length() > 0) {
                offlineDao.saveTrack(
                    OfflineTrack(
                        trackId = track.id,
                        title = track.title,
                        username = track.user.username,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs,
                        permalinkUrl = track.permalinkUrl,
                        userId = track.user.id,
                        userAvatarUrl = track.user.avatarUrl,
                        localPath = audioFile.absolutePath,
                        fileSizeBytes = audioFile.length(),
                    )
                )
                DebugLog.log("OfflineTrack", "Saved ${track.title} (${audioFile.length()} bytes)")
                Result.success(Unit)
            } else {
                audioFile.delete()
                Result.failure(Exception("Downloaded file is empty"))
            }
        } catch (e: Exception) {
            DebugLog.log("OfflineTrack", "Failed to save: ${e.message}")
            Result.failure(e)
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

    suspend fun getArtworkPath(trackId: Long): String? {
        val file = File(offlineDir, "${trackId}.jpg")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
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
                    artworkUrl = null,
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

    /**
     * Imports every audio file found directly inside a user-picked folder (SAF tree Uri, non-recursive)
     * into the offline library, reading title/artist/duration/embedded art via [MediaMetadataRetriever]
     * rather than a hand-rolled tag parser so more formats than mp3 work out of the box.
     */
    suspend fun importLocalFolder(treeUri: Uri): LocalImportResult = withContext(Dispatchers.IO) {
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
                    if (importLocalFile(docUri, name)) imported++ else skipped++
                }
            }
        } catch (e: Exception) {
            DebugLog.log("OfflineTrack", "importLocalFolder failed: ${e.message}")
        }
        LocalImportResult(imported, skipped)
    }

    private suspend fun importLocalFile(uri: Uri, displayName: String): Boolean {
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
            val ext = displayName.substringAfterLast('.', "mp3")
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
