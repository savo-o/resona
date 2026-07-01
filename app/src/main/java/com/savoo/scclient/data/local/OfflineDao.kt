package com.savoo.scclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.savoo.scclient.data.model.OfflineTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {
    @Query("SELECT * FROM offline_tracks ORDER BY savedAt DESC")
    fun getAllOfflineTracks(): Flow<List<OfflineTrack>>

    @Query("SELECT * FROM offline_tracks WHERE trackId = :trackId")
    suspend fun getOfflineTrack(trackId: Long): OfflineTrack?

    @Query("SELECT EXISTS(SELECT 1 FROM offline_tracks WHERE trackId = :trackId)")
    fun isOfflineTrack(trackId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM offline_tracks WHERE trackId = :trackId)")
    suspend fun isOfflineTrackSync(trackId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrack(track: OfflineTrack)

    @Query("DELETE FROM offline_tracks WHERE trackId = :trackId")
    suspend fun removeTrack(trackId: Long)

    @Query("SELECT SUM(fileSizeBytes) FROM offline_tracks")
    suspend fun getTotalSize(): Long?

    @Query("SELECT COUNT(*) FROM offline_tracks")
    suspend fun getTrackCount(): Int

    @Query("DELETE FROM offline_tracks")
    suspend fun clearAll()

    @Query("SELECT * FROM offline_tracks")
    suspend fun getAllOfflineTracksSync(): List<OfflineTrack>
}
