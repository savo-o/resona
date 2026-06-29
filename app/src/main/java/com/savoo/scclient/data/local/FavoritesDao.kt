package com.savoo.scclient.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllTracks(): Flow<List<FavoriteTrack>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    fun isTrackFavorite(trackId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrack(track: FavoriteTrack)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun removeTrack(trackId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isTrackFavoriteSync(trackId: Long): Boolean

    @Query("SELECT * FROM favorite_artists ORDER BY addedAt DESC")
    fun getAllArtists(): Flow<List<FavoriteArtist>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_artists WHERE artistId = :artistId)")
    fun isArtistFavorite(artistId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addArtist(artist: FavoriteArtist)

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

    @Query("DELETE FROM favorite_playlists WHERE playlistId = :playlistId")
    suspend fun removePlaylist(playlistId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_playlists WHERE playlistId = :playlistId)")
    suspend fun isPlaylistFavoriteSync(playlistId: Long): Boolean

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAllTracksSync(): List<FavoriteTrack>

    @Query("SELECT * FROM favorite_artists ORDER BY addedAt DESC")
    suspend fun getAllArtistsSync(): List<FavoriteArtist>

    @Query("SELECT * FROM favorite_playlists ORDER BY addedAt DESC")
    suspend fun getAllPlaylistsSync(): List<FavoritePlaylist>
}
