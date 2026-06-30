package com.savoo.scclient.data.remote

import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.SearchResponse
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.di.PlainHttpClient
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

data class ScProfile(
    val userId: Long,
    val username: String,
    val avatarUrl: String?,
    val fullName: String?,
    val permalinkUrl: String?,
    val followersCount: Long?,
)

data class ScImportResult(
    val tracks: List<FavoriteTrack>,
    val playlists: List<FavoritePlaylist>,
    val profile: ScProfile,
)

data class ScPlaylistImportResult(
    val playlists: List<FavoritePlaylist>,
    val profile: ScProfile,
)

sealed class DeepLinkResult {
    data class User(val userId: Long) : DeepLinkResult()
    data class Playlist(val playlistId: Long) : DeepLinkResult()
    data class Track(val trackId: Long) : DeepLinkResult()
}

@Singleton
class SoundCloudImportRepository @Inject constructor(
    @PlainHttpClient private val client: OkHttpClient,
    private val clientIdProvider: ClientIdProvider,
    private val moshi: Moshi,
) {
    suspend fun resolveProfile(input: String): Result<ScProfile> = withContext(Dispatchers.IO) {
        try {
            val url = buildResolveUrl(input)
            val clientId = getFreshClientId()

            val request = Request.Builder()
                .url("$url&client_id=$clientId")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    return@withContext Result.failure(
                        when (code) {
                            401, 403 -> IllegalStateException("Profile is private or not found")
                            404 -> IllegalStateException("Profile not found")
                            429 -> IllegalStateException("Rate limited. Try again later.")
                            else -> IllegalStateException("Error $code: Could not resolve profile")
                        }
                    )
                }
                response.body?.string() ?: throw IllegalStateException("Empty response")
            }

            val json = org.json.JSONObject(body)
            val userId = json.getLong("id")
            val username = json.getString("username")
            val avatarUrl = json.optString("avatar_url").ifEmpty { null }
            val fullName = json.optString("full_name").ifEmpty { null }
            val permalinkUrl = json.optString("permalink_url").ifEmpty { null }
            val followersCount = json.optLong("followers_count").takeIf { it > 0 }

            Result.success(
                ScProfile(
                    userId = userId,
                    username = username,
                    avatarUrl = avatarUrl,
                    fullName = fullName,
                    permalinkUrl = permalinkUrl,
                    followersCount = followersCount,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchLikedTracks(
        profile: ScProfile,
        onProgress: (current: Int) -> Unit = {},
    ): Result<ScImportResult> = withContext(Dispatchers.IO) {
        try {
            val clientId = getFreshClientId()
            val allTracks = mutableListOf<FavoriteTrack>()
            val allPlaylists = mutableListOf<FavoritePlaylist>()
            var nextUrl: String? = "https://api-v2.soundcloud.com/users/${profile.userId}/likes?client_id=$clientId&limit=200&linked_partitioning=true"
            var pageCount = 0
            val maxPages = 50

            while (nextUrl != null && pageCount < maxPages) {
                val request = Request.Builder().url(nextUrl).build()
                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 429) {
                            delay(2000L * (pageCount + 1))
                            return@withContext Result.failure(
                                IllegalStateException("Rate limited after $pageCount pages. Try again later.")
                            )
                        }
                        return@withContext Result.failure(
                            IllegalStateException("Error ${response.code} while fetching likes")
                        )
                    }
                    response.body?.string() ?: throw IllegalStateException("Empty response")
                }

                val json = org.json.JSONObject(body)
                val collection = json.optJSONArray("collection") ?: JSONArray()
                val nextHref = json.optString("next_href").ifEmpty { null }

                for (i in 0 until collection.length()) {
                    val item = collection.getJSONObject(i)
                    val kind = item.optString("kind", "unknown")
                    if (kind == "like") {
                        if (item.has("track")) {
                            val trackJson = item.getJSONObject("track")
                            val trackId = trackJson.getLong("id")
                            val title = trackJson.getString("title")
                            val durationMs = trackJson.optLong("duration", 0L)
                            val artworkUrl = trackJson.optString("artwork_url").ifEmpty { null }
                            val permalinkUrl = trackJson.optString("permalink_url").ifEmpty { null }
                            val userJson = trackJson.optJSONObject("user")
                            val userId = userJson?.optLong("id") ?: 0L
                            val username = userJson?.optString("username") ?: ""
                            val userAvatarUrl = userJson?.optString("avatar_url")?.ifEmpty { null }

                            allTracks.add(
                                FavoriteTrack(
                                    trackId = trackId,
                                    title = title,
                                    username = username,
                                    artworkUrl = artworkUrl,
                                    durationMs = durationMs,
                                    permalinkUrl = permalinkUrl,
                                    userId = userId,
                                    userAvatarUrl = userAvatarUrl,
                                )
                            )
                        } else if (item.has("playlist")) {
                            val plJson = item.getJSONObject("playlist")
                            val playlistId = plJson.getLong("id")
                            val title = plJson.getString("title")
                            val artworkUrl = plJson.optString("artwork_url").ifEmpty { null }
                            val trackCount = plJson.optInt("track_count", 0)
                            val permalinkUrl = plJson.optString("permalink_url").ifEmpty { null }
                            val userJson = plJson.optJSONObject("user")
                            val username = userJson?.optString("username") ?: ""

                            allPlaylists.add(
                                FavoritePlaylist(
                                    playlistId = playlistId,
                                    title = title,
                                    artworkUrl = artworkUrl,
                                    trackCount = trackCount,
                                    username = username,
                                    permalinkUrl = permalinkUrl,
                                )
                            )
                        }
                    }
                }

                onProgress(allTracks.size + allPlaylists.size)
                nextUrl = nextHref?.takeIf { it.isNotBlank() && it.startsWith("http") }?.let { href ->
                    val withClientId = if (href.contains("client_id")) href else "$href&client_id=$clientId"
                    if (withClientId.contains("limit=")) withClientId else "$withClientId&limit=200"
                }
                pageCount++

                if (nextUrl != null) delay(200)
            }

            Result.success(ScImportResult(tracks = allTracks, playlists = allPlaylists, profile = profile))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchLikedPlaylists(
        profile: ScProfile,
        onProgress: (current: Int) -> Unit = {},
    ): Result<ScPlaylistImportResult> = withContext(Dispatchers.IO) {
        try {
            val clientId = getFreshClientId()
            val allPlaylists = mutableListOf<FavoritePlaylist>()
            var nextUrl: String? = "https://api-v2.soundcloud.com/users/${profile.userId}/likes/playlists?client_id=$clientId&limit=200&linked_partitioning=true"
            var pageCount = 0
            val maxPages = 50

            while (nextUrl != null && pageCount < maxPages) {
                val request = Request.Builder().url(nextUrl).build()
                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 429) {
                            delay(2000L * (pageCount + 1))
                            return@withContext Result.failure(
                                IllegalStateException("Rate limited after $pageCount pages. Try again later.")
                            )
                        }
                        return@withContext Result.failure(
                            IllegalStateException("Error ${response.code} while fetching playlists")
                        )
                    }
                    response.body?.string() ?: throw IllegalStateException("Empty response")
                }

                val json = org.json.JSONObject(body)
                val collection = json.optJSONArray("collection") ?: JSONArray()
                val nextHref = json.optString("next_href").ifEmpty { null }

                for (i in 0 until collection.length()) {
                    val item = collection.getJSONObject(i)
                    val kind = item.optString("kind", "unknown")
                    if (kind == "like" && item.has("playlist")) {
                        val plJson = item.getJSONObject("playlist")
                        val playlistId = plJson.getLong("id")
                        val title = plJson.getString("title")
                        val artworkUrl = plJson.optString("artwork_url").ifEmpty { null }
                        val trackCount = plJson.optInt("track_count", 0)
                        val permalinkUrl = plJson.optString("permalink_url").ifEmpty { null }
                        val userJson = plJson.optJSONObject("user")
                        val username = userJson?.optString("username") ?: ""

                        allPlaylists.add(
                            FavoritePlaylist(
                                playlistId = playlistId,
                                title = title,
                                artworkUrl = artworkUrl,
                                trackCount = trackCount,
                                username = username,
                                permalinkUrl = permalinkUrl,
                            )
                        )
                    }
                }

                onProgress(allPlaylists.size)
                nextUrl = nextHref?.takeIf { it.isNotBlank() && it.startsWith("http") }?.let { href ->
                    val withClientId = if (href.contains("client_id")) href else "$href&client_id=$clientId"
                    if (withClientId.contains("limit=")) withClientId else "$withClientId&limit=200"
                }
                pageCount++

                if (nextUrl != null) delay(200)
            }

            Result.success(ScPlaylistImportResult(playlists = allPlaylists, profile = profile))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveUrl(url: String): Result<DeepLinkResult> = withContext(Dispatchers.IO) {
        try {
            val clientId = getFreshClientId()
            val resolveUrl = "https://api-v2.soundcloud.com/resolve?url=$url&client_id=$clientId"

            val request = Request.Builder().url(resolveUrl).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        when (response.code) {
                            401, 403 -> IllegalStateException("Profile is private or not found")
                            404 -> IllegalStateException("Not found")
                            429 -> IllegalStateException("Rate limited. Try again later.")
                            else -> IllegalStateException("Error ${response.code}")
                        }
                    )
                }
                response.body?.string() ?: throw IllegalStateException("Empty response")
            }

            val json = org.json.JSONObject(body)
            val kind = json.optString("kind", "")

            when (kind) {
                "user" -> Result.success(DeepLinkResult.User(json.getLong("id")))
                "playlist" -> Result.success(DeepLinkResult.Playlist(json.getLong("id")))
                "track" -> Result.success(DeepLinkResult.Track(json.getLong("id")))
                else -> Result.failure(IllegalStateException("Unknown type: $kind"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildResolveUrl(input: String): String {
        val cleanInput = input.trim()
            .replace("m.soundcloud.com", "soundcloud.com")
            .replace("www.soundcloud.com", "soundcloud.com")
        val profileUrl = when {
            cleanInput.startsWith("http") -> cleanInput
            cleanInput.startsWith("soundcloud.com/") -> "https://$cleanInput"
            cleanInput.contains("/") -> "https://soundcloud.com/$cleanInput"
            else -> "https://soundcloud.com/$cleanInput"
        }
        return "https://api-v2.soundcloud.com/resolve?url=$profileUrl"
    }

    private suspend fun getFreshClientId(): String {
        val cached = clientIdProvider.cachedOrFallback()
        if (cached.isNotBlank()) return cached
        return clientIdProvider.refresh()
    }
}
