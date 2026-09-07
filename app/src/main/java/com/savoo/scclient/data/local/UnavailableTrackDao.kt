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
}
