package com.savoo.scclient.data.remote

import com.savoo.scclient.data.model.KugouLyricsDownloadResponse
import com.savoo.scclient.data.model.KugouLyricsSearchResponse
import com.savoo.scclient.data.model.KugouSongSearchResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface KugouApi {
    @GET
    suspend fun searchSong(@Url url: String): KugouSongSearchResponse

    @GET
    suspend fun searchLyrics(@Url url: String): KugouLyricsSearchResponse

    @GET
    suspend fun downloadLyrics(@Url url: String): KugouLyricsDownloadResponse
}
