package com.savoo.scclient.data.remote

import com.savoo.scclient.data.model.LyricsSearchResult
import retrofit2.http.GET
import retrofit2.http.Query

interface LyricsApi {

    @GET("api/search")
    suspend fun search(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
    ): List<LyricsSearchResult>

    @GET("api/search")
    suspend fun searchQuery(@Query("q") query: String): List<LyricsSearchResult>

    @GET("api/search")
    suspend fun searchByTitle(@Query("track_name") trackName: String): List<LyricsSearchResult>
}
