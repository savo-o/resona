package com.savoo.scclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.savoo.scclient.data.model.PlayEvent
import kotlinx.coroutines.flow.Flow

data class ArtistListenStat(
    val artistId: Long,
    val artistName: String,
    val artworkUrl: String?,
    val totalMs: Long,
    val playCount: Int,
)

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun insert(event: PlayEvent)

    @Query("SELECT COALESCE(SUM(msPlayed), 0) FROM play_history")
    fun totalMsPlayed(): Flow<Long>

    @Query("SELECT COUNT(*) FROM play_history")
    fun totalPlays(): Flow<Int>

    @Query("""
        SELECT artistId, artistName, MAX(artworkUrl) AS artworkUrl, SUM(msPlayed) AS totalMs, COUNT(*) AS playCount
        FROM play_history
        GROUP BY artistId
        ORDER BY totalMs DESC
        LIMIT :limit
    """)
    fun topArtists(limit: Int = 10): Flow<List<ArtistListenStat>>
}
