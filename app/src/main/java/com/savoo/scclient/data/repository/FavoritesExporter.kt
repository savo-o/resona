package com.savoo.scclient.data.repository

import android.content.Context
import android.net.Uri
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class FavoritesExport(
    val version: Int = 1,
    val exportedAt: String,
    val tracks: List<FavoriteTrack>,
    val artists: List<FavoriteArtist>,
    val playlists: List<FavoritePlaylist>,
)

@Singleton
class FavoritesExporter @Inject constructor(
    private val favoritesDao: FavoritesDao,
    @ApplicationContext private val context: Context,
) {
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val tracks = favoritesDao.getAllTracksSync()
        val artists = favoritesDao.getAllArtistsSync()
        val playlists = favoritesDao.getAllPlaylistsSync()

        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", java.time.Instant.now().toString())

        root.put("tracks", JSONArray().apply {
            tracks.forEach { t ->
                put(JSONObject().apply {
                    put("trackId", t.trackId)
                    put("title", t.title)
                    put("username", t.username)
                    put("artworkUrl", t.artworkUrl ?: JSONObject.NULL)
                    put("durationMs", t.durationMs)
                    put("permalinkUrl", t.permalinkUrl ?: JSONObject.NULL)
                    put("userId", t.userId)
                    put("userAvatarUrl", t.userAvatarUrl ?: JSONObject.NULL)
                    put("addedAt", t.addedAt)
                })
            }
        })

        root.put("artists", JSONArray().apply {
            artists.forEach { a ->
                put(JSONObject().apply {
                    put("artistId", a.artistId)
                    put("username", a.username)
                    put("fullName", a.fullName ?: JSONObject.NULL)
                    put("avatarUrl", a.avatarUrl ?: JSONObject.NULL)
                    put("followersCount", a.followersCount ?: JSONObject.NULL)
                    put("permalinkUrl", a.permalinkUrl ?: JSONObject.NULL)
                    put("addedAt", a.addedAt)
                })
            }
        })

        root.put("playlists", JSONArray().apply {
            playlists.forEach { p ->
                put(JSONObject().apply {
                    put("playlistId", p.playlistId)
                    put("title", p.title)
                    put("artworkUrl", p.artworkUrl ?: JSONObject.NULL)
                    put("trackCount", p.trackCount)
                    put("username", p.username)
                    put("permalinkUrl", p.permalinkUrl ?: JSONObject.NULL)
                    put("addedAt", p.addedAt)
                })
            }
        })

        root.toString(2)
    }

    suspend fun exportToFile(uri: Uri): Result<Unit> = runCatching {
        val json = exportToJson()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.bufferedWriter().use { it.write(json) }
            } ?: throw IllegalStateException("Cannot open file for writing")
        }
    }

    suspend fun importFromFile(uri: Uri): Result<ImportResult> {
        return try {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    ?: throw IllegalStateException("Cannot open file for reading")
            }

            val root = JSONObject(text)
            val version = root.optInt("version", 1)
            if (version > 1) throw IllegalArgumentException("Unsupported export version: $version")

            var tracksImported = 0
            var artistsImported = 0
            var playlistsImported = 0

            root.optJSONArray("tracks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    favoritesDao.addTrack(
                        FavoriteTrack(
                            trackId = t.getLong("trackId"),
                            title = t.getString("title"),
                            username = t.getString("username"),
                            artworkUrl = t.safeOptString("artworkUrl"),
                            durationMs = t.getLong("durationMs"),
                            permalinkUrl = t.safeOptString("permalinkUrl"),
                            userId = t.getLong("userId"),
                            userAvatarUrl = t.safeOptString("userAvatarUrl"),
                            addedAt = t.optLong("addedAt", System.currentTimeMillis()),
                        )
                    )
                    tracksImported++
                }
            }

            root.optJSONArray("artists")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    favoritesDao.addArtist(
                        FavoriteArtist(
                            artistId = a.getLong("artistId"),
                            username = a.getString("username"),
                            fullName = a.safeOptString("fullName"),
                            avatarUrl = a.safeOptString("avatarUrl"),
                            followersCount = a.optLong("followersCount").takeIf { it > 0 },
                            permalinkUrl = a.safeOptString("permalinkUrl"),
                            addedAt = a.optLong("addedAt", System.currentTimeMillis()),
                        )
                    )
                    artistsImported++
                }
            }

            root.optJSONArray("playlists")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    favoritesDao.addPlaylist(
                        FavoritePlaylist(
                            playlistId = p.getLong("playlistId"),
                            title = p.getString("title"),
                            artworkUrl = p.safeOptString("artworkUrl"),
                            trackCount = p.getInt("trackCount"),
                            username = p.getString("username"),
                            permalinkUrl = p.safeOptString("permalinkUrl"),
                            addedAt = p.optLong("addedAt", System.currentTimeMillis()),
                        )
                    )
                    playlistsImported++
                }
            }

            Result.success(ImportResult(tracksImported, artistsImported, playlistsImported))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class ImportResult(
        val tracks: Int,
        val artists: Int,
        val playlists: Int,
    ) {
        val total: Int get() = tracks + artists + playlists
    }
}

private fun org.json.JSONObject.safeOptString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotEmpty() && it != "null" }
}
