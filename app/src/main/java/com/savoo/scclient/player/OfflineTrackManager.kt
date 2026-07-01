package com.savoo.scclient.player

import android.content.Context
import android.util.Log
import com.savoo.scclient.data.local.OfflineDao
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.repository.TrackRepository
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
                Log.d("OfflineTrack", "Saved ${track.title} (${audioFile.length()} bytes)")
                Result.success(Unit)
            } else {
                audioFile.delete()
                Result.failure(Exception("Downloaded file is empty"))
            }
        } catch (e: Exception) {
            Log.e("OfflineTrack", "Failed to save: ${e.message}")
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
                Log.d("OfflineTrack", "Removed offline track: ${track.title}")
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
            Log.d("OfflineTrack", "Cleared all offline tracks")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLocalPath(trackId: Long): String? {
        val offlineTrack = offlineDao.getOfflineTrack(trackId)
        if (offlineTrack == null) {
            Log.d("OfflineTrack", "getLocalPath($trackId): not in DB")
            return null
        }
        val file = File(offlineTrack.localPath)
        val exists = file.exists()
        val size = if (exists) file.length() else 0L
        Log.d("OfflineTrack", "getLocalPath($trackId): exists=$exists, size=$size, path=${offlineTrack.localPath}")
        return if (exists && size > 0) file.absolutePath else null
    }

    suspend fun getArtworkPath(trackId: Long): String? {
        val file = File(offlineDir, "${trackId}.jpg")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }
}
