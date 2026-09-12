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

data class RecentTrack(
    val trackId: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val artworkUrl: String?,
    val lastPlayedAt: Long,
)

data class TrackListenStat(
    val trackId: Long,
    val title: String,
    val artistName: String,
    val artworkUrl: String?,
    val totalMs: Long,
    val playCount: Int,
)

const val MIN_COUNTED_MS = 5_000L

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun insert(event: PlayEvent): Long

    @Query("UPDATE play_history SET msPlayed = :msPlayed WHERE id = :id")
    suspend fun updateMsPlayed(id: Long, msPlayed: Long)

    @Query("""
        SELECT trackId, title, artistId, artistName, artworkUrl, MAX(playedAt) AS lastPlayedAt
        FROM play_history
        WHERE hiddenFromHistory = 0
        GROUP BY trackId
        ORDER BY lastPlayedAt DESC
        LIMIT :limit
    """)
    fun observeRecentTracks(limit: Int = 30): Flow<List<RecentTrack>>

    @Query("SELECT COALESCE(SUM(msPlayed), 0) FROM play_history WHERE playedAt >= :since")
    fun totalMsPlayed(since: Long = 0L): Flow<Long>

    @Query("SELECT COUNT(*) FROM play_history WHERE msPlayed >= :minMs AND playedAt >= :since")
    fun totalPlays(since: Long = 0L, minMs: Long = MIN_COUNTED_MS): Flow<Int>

    @Query("""
        SELECT artistId, artistName, MAX(artworkUrl) AS artworkUrl, SUM(msPlayed) AS totalMs, COUNT(*) AS playCount
        FROM play_history
        WHERE msPlayed >= :minMs AND playedAt >= :since
        GROUP BY artistId
        ORDER BY totalMs DESC
        LIMIT :limit
    """)
    fun topArtists(limit: Int = 10, since: Long = 0L, minMs: Long = MIN_COUNTED_MS): Flow<List<ArtistListenStat>>

    @Query("SELECT * FROM play_history WHERE msPlayed >= :minMs ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recentEvents(limit: Int = 500, minMs: Long = MIN_COUNTED_MS): List<PlayEvent>

    @Query("SELECT * FROM play_history WHERE hiddenFromHistory = 0 ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 1000): Flow<List<PlayEvent>>

    @Query("UPDATE play_history SET hiddenFromHistory = :hidden WHERE id IN (:ids)")
    suspend fun setHiddenFromHistory(ids: List<Long>, hidden: Boolean)

    @Query("DELETE FROM play_history WHERE trackId = :trackId")
    suspend fun deleteTrackHistory(trackId: Long)

    @Query("UPDATE play_history SET hiddenFromHistory = 1 WHERE hiddenFromHistory = 0")
    suspend fun clearHistory()

    @Query("""
        SELECT trackId, title, artistName, MAX(artworkUrl) AS artworkUrl, SUM(msPlayed) AS totalMs, COUNT(*) AS playCount
        FROM play_history
        WHERE msPlayed >= :minMs AND playedAt >= :since
        GROUP BY trackId
        ORDER BY totalMs DESC
        LIMIT :limit
    """)
    fun topTracks(limit: Int = 10, since: Long = 0L, minMs: Long = MIN_COUNTED_MS): Flow<List<TrackListenStat>>

    @Query("""
        SELECT genre FROM play_history
        WHERE genre IS NOT NULL AND genre != '' AND msPlayed >= :minMs AND playedAt >= :since
        GROUP BY genre
        ORDER BY SUM(msPlayed) DESC
        LIMIT 1
    """)
    fun topGenre(since: Long = 0L, minMs: Long = MIN_COUNTED_MS): Flow<String?>
}
