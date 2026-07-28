package com.savoo.scclient.data.remote

import com.savoo.scclient.data.model.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubReleaseApi {

    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 15,
    ): List<GitHubRelease>
}
