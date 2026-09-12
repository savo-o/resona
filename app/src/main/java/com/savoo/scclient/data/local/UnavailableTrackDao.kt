package com.savoo.scclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.savoo.scclient.data.model.UnavailableTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnavailableTrackDao {
    @Query("SELECT * FROM unavailable_tracks WHERE trackId = :trackId")
    suspend fun get(trackId: Long): UnavailableTrackEntity?

    @Query("SELECT * FROM unavailable_tracks")
    fun observeAll(): Flow<List<UnavailableTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(entry: UnavailableTrackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markIfAbsent(entry: UnavailableTrackEntity)

    @Query("DELETE FROM unavailable_tracks WHERE trackId = :trackId")
    suspend fun clear(trackId: Long)

    @Query("DELETE FROM unavailable_tracks WHERE markedAt < :before")
    suspend fun clearExpired(before: Long)
}
