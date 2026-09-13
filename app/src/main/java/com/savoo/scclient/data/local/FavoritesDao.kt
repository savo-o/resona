package com.savoo.scclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.paging.PagingSource
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import kotlinx.coroutines.flow.Flow

data class FavoriteTrackArtist(
    val userId: Long,
    val username: String,
    val userAvatarUrl: String?,
)

data class FavoriteTrackSource(
    val trackId: Long,
    val source: String,
)

@Dao
interface FavoritesDao {
    @RawQuery(observedEntities = [FavoriteTrack::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, FavoriteTrack>

    @RawQuery
    suspend fun queryTracks(query: SupportSQLiteQuery): List<FavoriteTrack>

    @RawQuery
    suspend fun queryTrackIds(query: SupportSQLiteQuery): List<Long>

    @RawQuery
    suspend fun queryCount(query: SupportSQLiteQuery): Int

    @Query("SELECT COUNT(*) FROM favorites")
    fun observeTrackCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun trackCount(): Int

    @Query("SELECT COUNT(*) FROM favorite_artists")
    suspend fun artistCount(): Int

    @Query("SELECT COUNT(*) FROM favorite_playlists")
    suspend fun playlistCount(): Int

    @Query("SELECT trackId FROM favorites")
    fun observeTrackIds(): Flow<List<Long>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC LIMIT :limit")
    fun observeLatestTracks(limit: Int): Flow<List<FavoriteTrack>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC, trackId LIMIT :limit OFFSET :offset")
    suspend fun getTracksPage(limit: Int, offset: Int): List<FavoriteTrack>

    @Query("SELECT * FROM favorites ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomTracks(limit: Int): List<FavoriteTrack>

    @Query("SELECT * FROM favorites WHERE trackId IN (:ids)")
    suspend fun getTracksByIds(ids: List<Long>): List<FavoriteTrack>

    @Query("SELECT trackId FROM favorites WHERE trackId IN (:ids)")
    suspend fun filterFavoriteTrackIds(ids: List<Long>): List<Long>

    @Query("SELECT DISTINCT userId FROM favorites WHERE userId != 0")
    suspend fun getTrackArtistIds(): List<Long>

    @Query("""
        SELECT userId, username, MAX(userAvatarUrl) AS userAvatarUrl
        FROM favorites
        WHERE userId != 0
        GROUP BY userId
        ORDER BY MAX(addedAt) DESC
    """)
    fun observeTrackArtists(): Flow<List<FavoriteTrackArtist>>

    @Query("SELECT trackId, source FROM favorites WHERE source != 'LOCAL'")
    suspend fun getNonLocalTrackSources(): List<FavoriteTrackSource>

    @Query("DELETE FROM favorites WHERE trackId IN (:ids)")
    suspend fun removeTracks(ids: List<Long>)

    @Query("DELETE FROM favorites")
    suspend fun removeAllTracks()

    @Query("DELETE FROM favorite_playlists")
    suspend fun removeAllPlaylists()

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    fun isTrackFavorite(trackId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrack(track: FavoriteTrack)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTracks(tracks: List<FavoriteTrack>)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun removeTrack(trackId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isTrackFavoriteSync(trackId: Long): Boolean

    @Query("SELECT * FROM favorites WHERE trackId = :trackId LIMIT 1")
    suspend fun getTrackSync(trackId: Long): FavoriteTrack?

    @Query("UPDATE favorites SET source = :source WHERE trackId = :trackId")
    suspend fun updateTrackSource(trackId: Long, source: String)

    @Query("SELECT * FROM favorite_artists ORDER BY addedAt DESC")
    fun getAllArtists(): Flow<List<FavoriteArtist>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_artists WHERE artistId = :artistId)")
    fun isArtistFavorite(artistId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addArtist(artist: FavoriteArtist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addArtists(artists: List<FavoriteArtist>)

    @Query("DELETE FROM favorite_artists WHERE artistId = :artistId")
    suspend fun removeArtist(artistId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_artists WHERE artistId = :artistId)")
    suspend fun isArtistFavoriteSync(artistId: Long): Boolean

    @Query("SELECT * FROM favorite_playlists ORDER BY addedAt DESC")
    fun getAllPlaylists(): Flow<List<FavoritePlaylist>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_playlists WHERE playlistId = :playlistId)")
    fun isPlaylistFavorite(playlistId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylist(playlist: FavoritePlaylist)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylists(playlists: List<FavoritePlaylist>)

    @Query("DELETE FROM favorite_playlists WHERE playlistId = :playlistId")
    suspend fun removePlaylist(playlistId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_playlists WHERE playlistId = :playlistId)")
    suspend fun isPlaylistFavoriteSync(playlistId: Long): Boolean

    @Query("SELECT * FROM favorite_artists ORDER BY addedAt DESC")
    suspend fun getAllArtistsSync(): List<FavoriteArtist>

    @Query("SELECT * FROM favorite_playlists ORDER BY addedAt DESC")
    suspend fun getAllPlaylistsSync(): List<FavoritePlaylist>
}
