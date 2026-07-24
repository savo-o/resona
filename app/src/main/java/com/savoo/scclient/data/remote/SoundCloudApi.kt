package com.savoo.scclient.data.remote

import com.savoo.scclient.data.model.LikeItem
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.SearchResponse
import com.savoo.scclient.data.model.StreamUrlResponse
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface SoundCloudApi {

    @GET("search/tracks")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
    ): SearchResponse<Track>

    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
    ): SearchResponse<User>

    @GET("search/playlists")
    suspend fun searchPlaylists(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0,
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

    @Headers("Content-Type: application/json")
    @PUT("users/{userId}/track_likes/{trackId}")
    suspend fun likeTrack(@Path("userId") userId: Long, @Path("trackId") trackId: Long): Response<Unit>

    @DELETE("users/{userId}/track_likes/{trackId}")
    suspend fun unlikeTrack(@Path("userId") userId: Long, @Path("trackId") trackId: Long): Response<Unit>

    @GET
    suspend fun getNextPage(@Url url: String): SearchResponse<Track>

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

    @GET("playlists/{id}")
    suspend fun getPlaylist(@Path("id") id: Long): Playlist
}
