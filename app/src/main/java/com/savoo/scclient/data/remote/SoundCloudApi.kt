package com.savoo.scclient.data.remote

import com.savoo.scclient.data.model.ChartItem
import com.savoo.scclient.data.model.LikeItem
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.SearchResponse
import com.savoo.scclient.data.model.StreamUrlResponse
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.TrackComment
import com.savoo.scclient.data.model.User
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface SoundCloudApi {

    @GET("search/tracks")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("linked_partitioning") linkedPartitioning: Boolean = true,
    ): SearchResponse<Track>

    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("linked_partitioning") linkedPartitioning: Boolean = true,
    ): SearchResponse<User>

    @GET("search/playlists")
    suspend fun searchPlaylists(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("linked_partitioning") linkedPartitioning: Boolean = true,
    ): SearchResponse<Playlist>

    @GET("me")
    suspend fun getMe(): User

    // The real api-v2 endpoint for a user's likes - same one already proven to work (for public
    // profiles) in SoundCloudImportRepository; used here with the authenticated client so it also
    // works for the logged-in user's own (possibly private) likes.
    @GET("users/{id}/likes")
    suspend fun getUserLikes(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 200,
        @Query("linked_partitioning") linkedPartitioning: Boolean = true,
    ): SearchResponse<LikeItem>

    @GET
    suspend fun getNextLikesPage(@Url url: String): SearchResponse<LikeItem>

    @PUT("users/{userId}/track_likes/{trackId}")
    suspend fun likeTrack(@Path("userId") userId: Long, @Path("trackId") trackId: Long): Response<Unit>

    @DELETE("users/{userId}/track_likes/{trackId}")
    suspend fun unlikeTrack(@Path("userId") userId: Long, @Path("trackId") trackId: Long): Response<Unit>

    @GET
    suspend fun getNextPage(@Url url: String): SearchResponse<Track>

    @GET
    suspend fun getNextUsersPage(@Url url: String): SearchResponse<User>

    @GET
    suspend fun getNextPlaylistsPage(@Url url: String): SearchResponse<Playlist>

    @GET
    suspend fun resolveStreamUrl(@Url transcodingUrl: String): StreamUrlResponse

    @GET("tracks/{id}")
    suspend fun getTrack(@Path("id") id: Long): Track

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Long): User

    @GET("users/{id}/tracks")
    suspend fun getUserTracks(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SearchResponse<Track>

    @GET("tracks/{id}/related")
    suspend fun getRelatedTracks(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): SearchResponse<Track>

    @GET("tracks/{id}/comments")
    suspend fun getTrackComments(
        @Path("id") id: Long,
        @Query("threaded") threaded: Int = 0,
        @Query("filter_replies") filterReplies: Int = 1,
        @Query("limit") limit: Int = 100,
    ): SearchResponse<TrackComment>

    @GET("users/{id}/toptracks")
    suspend fun getUserTopTracks(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 10,
    ): SearchResponse<Track>

    @GET("users/{id}/albums")
    suspend fun getUserAlbums(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 20,
    ): SearchResponse<Playlist>

    @GET("users/{id}/playlists_without_albums")
    suspend fun getUserPlaylists(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 20,
    ): SearchResponse<Playlist>

    @GET("users/{id}/relatedartists")
    suspend fun getRelatedArtists(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 12,
    ): SearchResponse<User>

    @GET("playlists/{id}")
    suspend fun getPlaylist(@Path("id") id: Long): Playlist

    @GET("charts")
    suspend fun getCharts(
        @Query("kind") kind: String,
        @Query("genre") genre: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SearchResponse<ChartItem>
}
