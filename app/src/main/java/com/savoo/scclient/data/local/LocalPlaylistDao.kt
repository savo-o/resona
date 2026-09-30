package com.savoo.scclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.savoo.scclient.data.model.LocalPlaylist
import com.savoo.scclient.data.model.LocalPlaylistSummary
import com.savoo.scclient.data.model.LocalPlaylistTrack
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LocalPlaylistDao {
    @Query(
        """
        SELECT p.id, p.title, p.createdAt, p.updatedAt,
            (SELECT COUNT(*) FROM local_playlist_tracks t WHERE t.playlistId = p.id) AS trackCount,
            (SELECT t.artworkUrl FROM local_playlist_tracks t
                WHERE t.playlistId = p.id AND t.artworkUrl IS NOT NULL ORDER BY t.position LIMIT 1) AS artworkUrl
        FROM local_playlists p ORDER BY p.updatedAt DESC
        """
    )
    abstract fun observeSummaries(): Flow<List<LocalPlaylistSummary>>

    @Query("SELECT * FROM local_playlists WHERE id = :playlistId")
    abstract fun observePlaylist(playlistId: Long): Flow<LocalPlaylist?>

    @Query("SELECT * FROM local_playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    abstract fun observeTracks(playlistId: Long): Flow<List<LocalPlaylistTrack>>

    @Query("SELECT COUNT(*) FROM local_playlists")
    abstract suspend fun playlistCount(): Int

    @Query("SELECT * FROM local_playlists ORDER BY id")
    abstract suspend fun getAllSync(): List<LocalPlaylist>

    @Query("SELECT * FROM local_playlist_tracks ORDER BY playlistId, position")
    abstract suspend fun getAllTracksSync(): List<LocalPlaylistTrack>

    @Query("SELECT id FROM local_playlists WHERE title = :title AND createdAt = :createdAt LIMIT 1")
    abstract suspend fun findId(title: String, createdAt: Long): Long?

    @Insert
    abstract suspend fun insert(playlist: LocalPlaylist): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTracks(tracks: List<LocalPlaylistTrack>): List<Long>

    @Query("SELECT trackId FROM local_playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun trackIds(playlistId: Long): List<Long>

    @Query("SELECT MAX(position) FROM local_playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int?

    @Query("UPDATE local_playlists SET title = :title, updatedAt = :updatedAt WHERE id = :playlistId")
    abstract suspend fun rename(playlistId: Long, title: String, updatedAt: Long)

    @Query("UPDATE local_playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    abstract suspend fun touch(playlistId: Long, updatedAt: Long)

    @Query("DELETE FROM local_playlist_tracks WHERE playlistId = :playlistId AND trackId IN (:trackIds)")
    abstract suspend fun deleteTracks(playlistId: Long, trackIds: List<Long>)

    @Query("DELETE FROM local_playlist_tracks WHERE playlistId = :playlistId")
    abstract suspend fun deleteAllTracks(playlistId: Long)

    @Query("DELETE FROM local_playlists WHERE id = :playlistId")
    abstract suspend fun deletePlaylistRow(playlistId: Long)

    @Transaction
    open suspend fun appendTracks(playlistId: Long, tracks: List<LocalPlaylistTrack>): Int {
        val existing = trackIds(playlistId).toSet()
        var position = (maxPosition(playlistId) ?: -1) + 1
        val fresh = tracks.distinctBy { it.trackId }
            .filter { it.trackId !in existing }
            .map { it.copy(playlistId = playlistId, position = position++) }
        if (fresh.isEmpty()) return 0
        insertTracks(fresh)
        touch(playlistId, System.currentTimeMillis())
        return fresh.size
    }

    @Transaction
    open suspend fun createWithTracks(playlist: LocalPlaylist, tracks: List<LocalPlaylistTrack>): Long {
        val id = insert(playlist)
        appendTracks(id, tracks)
        return id
    }

    @Transaction
    open suspend fun removeTracks(playlistId: Long, trackIds: List<Long>) {
        trackIds.chunked(SQLITE_MAX_IDS_PER_QUERY).forEach { deleteTracks(playlistId, it) }
        touch(playlistId, System.currentTimeMillis())
    }

    @Transaction
    open suspend fun deletePlaylist(playlistId: Long) {
        deleteAllTracks(playlistId)
        deletePlaylistRow(playlistId)
    }
}
