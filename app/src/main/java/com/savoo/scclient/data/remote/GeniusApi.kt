package com.savoo.scclient.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface GeniusApi {
    @GET("api/search/multi")
    suspend fun search(@Query("q") query: String): ResponseBody

    @GET
    suspend fun fetchPage(@Url url: String): ResponseBody

    @GET("api/referents")
    suspend fun referents(
        @Query("song_id") songId: Long,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 50,
        @Query("text_format") textFormat: String = "plain",
    ): ResponseBody
}
