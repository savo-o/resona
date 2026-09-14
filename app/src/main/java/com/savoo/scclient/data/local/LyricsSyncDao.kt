package com.savoo.scclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.savoo.scclient.data.model.LyricsSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsSyncDao {
    @Query("SELECT * FROM lyrics_sync WHERE trackId = :trackId AND provider = :provider LIMIT 1")
    fun observe(trackId: Long, provider: String): Flow<LyricsSyncEntity?>

    @Query("SELECT * FROM lyrics_sync ORDER BY updatedAt DESC")
    suspend fun all(): List<LyricsSyncEntity>

    @Query("SELECT COUNT(*) FROM lyrics_sync")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LyricsSyncEntity)

    @Query("DELETE FROM lyrics_sync WHERE trackId = :trackId AND provider = :provider")
    suspend fun delete(trackId: Long, provider: String)
}
