package com.savoo.scclient.data.repository

import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.SoundCloudApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepository @Inject constructor(
    private val api: SoundCloudApi,
    private val tokenStore: TokenStore,
) {
    private val TAG = "TrackRepository"
    private var cachedUserId: Long? = null

    private suspend fun currentUserId(): Long? {
        cachedUserId?.let { return it }
        val id = runCatching { api.getMe().id }.getOrNull()
        cachedUserId = id
        return id
    }

    suspend fun searchTracks(query: String): List<Track> =
        api.searchTracks(query = query).collection

    suspend fun searchUsers(query: String): List<User> =
        api.searchUsers(query = query).collection

    suspend fun getMe(): User = api.getMe()

    suspend fun getTrack(id: Long): Track {
        val track = api.getTrack(id)
        android.util.Log.d("TrackRepository", "getTrack($id): media=${track.media}, transcodings=${track.media?.transcodings?.size}")
        return track
    }

    // The authenticated user's own likes, via the same real JSON API (users/{id}/likes) already
    // proven to work for public profiles in SoundCloudImportRepository - used here through the
    // authenticated client so it also covers the logged-in user's own private likes. Each item is
    // a {kind, track} wrapper, not a flat Track, so it can't reuse the plain SearchResponse<Track>
    // pagination helpers.
    suspend fun getLikedTracks(): List<Track> {
        if (tokenStore.accessToken.isNullOrEmpty()) return emptyList()
        val userId = currentUserId() ?: return emptyList()
        val allTracks = mutableListOf<Track>()
        var response = api.getUserLikes(userId, limit = 200)
        allTracks.addAll(response.collection.mapNotNull { it.track.takeIf { _ -> it.kind == "like" } })
        var nextUrl = response.nextHref
        while (nextUrl != null && allTracks.size < 1000) {
            response = api.getNextLikesPage(nextUrl)
            allTracks.addAll(response.collection.mapNotNull { it.track.takeIf { _ -> it.kind == "like" } })
            nextUrl = response.nextHref
        }
        return allTracks
    }

    // Confirmed against the real soundcloud.com web client's own network calls: PUT/DELETE
    // users/{ownUserId}/track_likes/{trackId} (not "me" - the real user id).
    suspend fun likeTrack(trackId: Long) {
        val userId = currentUserId() ?: error("Not logged in")
        val response = api.likeTrack(userId, trackId)
        if (!response.isSuccessful) error("likeTrack failed: HTTP ${response.code()}")
    }

    suspend fun unlikeTrack(trackId: Long) {
        val userId = currentUserId() ?: error("Not logged in")
        val response = api.unlikeTrack(userId, trackId)
        if (!response.isSuccessful) error("unlikeTrack failed: HTTP ${response.code()}")
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

    // Single-page, no pagination - for lightweight "does this artist have anything new" lookups
    // (e.g. the Home mix) where fetching an artist's full catalog would be wasteful.
    suspend fun getUserTracksPage(userId: Long, limit: Int = 20): List<Track> =
        api.getUserTracks(userId, limit = limit).collection

    suspend fun getPlaylist(id: Long): Playlist = api.getPlaylist(id)

    suspend fun resolvePlayableUrl(track: Track): String? {
        val transcoding = track.media?.transcodings
            ?.firstOrNull { it.format.protocol == "progressive" }
            ?: track.media?.transcodings?.firstOrNull()
            ?: return null
        return api.resolveStreamUrl(transcoding.url).url
    }
}
