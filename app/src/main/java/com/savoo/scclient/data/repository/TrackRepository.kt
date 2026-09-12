package com.savoo.scclient.data.repository

import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.SearchResponse
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.SoundCloudApi
import com.savoo.scclient.data.remote.WebViewApiBridge
import com.savoo.scclient.debug.DebugLog
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

data class SearchPage<T>(val items: List<T>, val nextHref: String?)

private fun <T> SearchResponse<T>.toPage(): SearchPage<T> =
    SearchPage(collection, nextHref?.takeIf { it.isNotBlank() })

@Singleton
class TrackRepository @Inject constructor(
    private val api: SoundCloudApi,
    private val tokenStore: TokenStore,
    private val webBridge: WebViewApiBridge,
) {
    private val TAG = "TrackRepository"
    private var cachedUserId: Long? = null

    private suspend fun currentUserId(): Long? {
        cachedUserId?.let { return it }
        val id = runCatching { api.getMe().id }.getOrNull()
        cachedUserId = id
        return id
    }

    suspend fun searchTracks(query: String, limit: Int = SEARCH_PAGE_SIZE): SearchPage<Track> =
        api.searchTracks(query = query, limit = limit).toPage()

    suspend fun searchUsers(query: String, limit: Int = SEARCH_PAGE_SIZE): SearchPage<User> =
        api.searchUsers(query = query, limit = limit).toPage()

    suspend fun getMe(): User = api.getMe()

    suspend fun getTrack(id: Long): Track {
        val track = api.getTrack(id)
        DebugLog.log(TAG, "getTrack($id): media=${track.media}, transcodings=${track.media?.transcodings?.size}")
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
    // users/{ownUserId}/track_likes/{trackId} (not "me" - the real user id). Routed through
    // WebViewApiBridge because SoundCloud's edge protection (DataDome) blocks the PUT (create a
    // like) from a plain HTTP client even with browser-shaped headers, but allows it from an
    // actual WebView JS engine's own fetch.
    suspend fun likeTrack(trackId: Long) {
        val userId = currentUserId() ?: error("Not logged in")
        val code = webBridge.likeTrack(userId, trackId)
        if (code !in 200..299) error("likeTrack failed via WebView: HTTP $code")
    }

    suspend fun unlikeTrack(trackId: Long) {
        val userId = currentUserId() ?: error("Not logged in")
        val code = webBridge.unlikeTrack(userId, trackId)
        // A 404 here just means it was never liked online in the first place (e.g. a local-only
        // favorite) - that's already the desired end state, not a real failure.
        if (code !in 200..299 && code != 404) error("unlikeTrack failed via WebView: HTTP $code")
    }

    suspend fun searchPlaylists(query: String, limit: Int = SEARCH_PAGE_SIZE): SearchPage<Playlist> =
        api.searchPlaylists(query = query, limit = limit).toPage()

    suspend fun searchTracksPage(nextHref: String): SearchPage<Track> = api.getNextPage(nextHref).toPage()

    suspend fun searchUsersPage(nextHref: String): SearchPage<User> = api.getNextUsersPage(nextHref).toPage()

    suspend fun searchPlaylistsPage(nextHref: String): SearchPage<Playlist> =
        api.getNextPlaylistsPage(nextHref).toPage()

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
        return allTracks.distinctBy { it.id }
    }

    // Single-page, no pagination - for lightweight "does this artist have anything new" lookups
    // (e.g. the Home mix) where fetching an artist's full catalog would be wasteful.
    suspend fun getUserTracksPage(userId: Long, limit: Int = 20): List<Track> =
        api.getUserTracks(userId, limit = limit).collection

    suspend fun getPlaylist(id: Long): Playlist = api.getPlaylist(id)

    suspend fun getCharts(limit: Int = 50): List<Track> =
        api.getCharts(kind = "trending", genre = "soundcloud:genres:all-music", limit = limit).collection.map { it.track }

    data class PlayableStream(val url: String, val isHls: Boolean)

    suspend fun resolvePlayableStream(track: Track): PlayableStream? {
        val candidates = (track.media?.transcodings ?: return null)
            .filter { it.format.protocol == "progressive" || it.format.protocol == "hls" }
            .sortedByDescending { it.format.protocol == "progressive" }
        var lastNotFound: HttpException? = null
        for (candidate in candidates) {
            try {
                val url = api.resolveStreamUrl(candidate.url).url
                return PlayableStream(url, isHls = candidate.format.protocol == "hls")
            } catch (e: HttpException) {
                if (e.code() != 404) throw e
                lastNotFound = e
            }
        }
        lastNotFound?.let { throw it }
        return null
    }

    companion object {
        const val SEARCH_PAGE_SIZE = 25
    }
}
