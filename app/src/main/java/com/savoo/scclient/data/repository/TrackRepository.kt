package com.savoo.scclient.data.repository

import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.SoundCloudApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.savoo.scclient.di.PlainHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepository @Inject constructor(
    private val api: SoundCloudApi,
    private val tokenStore: TokenStore,
    @PlainHttpClient private val plainClient: OkHttpClient,
) {
    private val TAG = "TrackRepository"

    suspend fun searchTracks(query: String): List<Track> =
        api.searchTracks(query = query).collection

    suspend fun searchUsers(query: String): List<User> =
        api.searchUsers(query = query).collection

    suspend fun getMe(): User = api.getMe()

    suspend fun getTrack(id: Long): Track = api.getTrack(id)

    suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        val token = tokenStore.accessToken
        if (token.isNullOrEmpty()) {
            return@withContext emptyList()
        }

        val client = plainClient
        val request = Request.Builder()
            .url("https://soundcloud.com/you/likes")
            .header("Authorization", "OAuth $token")
            .header("Cookie", tokenStore.webCookies.orEmpty())
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .build()

        val response = client.newCall(request).execute()
        val html = response.use { it.body?.string().orEmpty() }

        if (response.code != 200) {
            return@withContext emptyList()
        }

        val regex = Regex("""window\.__sc_hydration\s*=\s*(\[[\s\S]*?\])\s*;?\s*</script>""")
        val match = regex.find(html)
        if (match == null) {
            return@withContext emptyList()
        }

        val json = match.groupValues[1]

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val type = Types.newParameterizedType(List::class.java, Map::class.java, Any::class.java)
        val adapter = moshi.adapter<List<Map<String, Any>>>(type)
        val items = adapter.fromJson(json) ?: return@withContext emptyList()

        val tracksJson = mutableListOf<Map<String, Any>>()
        for (item in items) {
            val data = item["data"] as? Map<*, *> ?: continue
            val collection = data["collection"] as? List<*> ?: continue
            for (entry in collection) {
                val track = entry as? Map<*, *> ?: continue
                if (track.containsKey("title") && track.containsKey("user")) {
                    @Suppress("UNCHECKED_CAST")
                    tracksJson.add(track as Map<String, Any>)
                }
            }
        }

        val trackType = Types.newParameterizedType(List::class.java, Track::class.java)
        val trackAdapter = moshi.adapter<List<Track>>(trackType)
        trackAdapter.fromJsonValue(tracksJson) ?: emptyList()
    }

    suspend fun searchPlaylists(query: String): List<Playlist> =
        api.searchPlaylists(query = query).collection

    suspend fun getUser(id: Long): User = api.getUser(id)

    suspend fun getUserTracks(userId: Long): List<Track> {
        val allTracks = mutableListOf<Track>()
        var response = api.getUserTracks(userId, limit = 50)
        allTracks.addAll(response.collection)
        var nextUrl = response.nextHref
        while (nextUrl != null && allTracks.size < 500) {
            response = api.getNextPage(nextUrl)
            allTracks.addAll(response.collection)
            nextUrl = response.nextHref
        }
        return allTracks
    }

    suspend fun getPlaylist(id: Long): Playlist = api.getPlaylist(id)

    suspend fun resolvePlayableUrl(track: Track): String? {
        val transcoding = track.media?.transcodings
            ?.firstOrNull { it.format.protocol == "progressive" }
            ?: track.media?.transcodings?.firstOrNull()
            ?: return null
        return api.resolveStreamUrl(transcoding.url).url
    }
}
