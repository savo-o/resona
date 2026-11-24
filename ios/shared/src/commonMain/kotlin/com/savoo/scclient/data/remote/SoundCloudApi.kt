package com.savoo.scclient.data.remote

import com.savoo.scclient.data.model.ChartItem
import com.savoo.scclient.data.model.LikeItem
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.ProfileFeedItem
import io.ktor.client.request.put
import io.ktor.client.request.delete
import com.savoo.scclient.data.model.SearchResponse
import com.savoo.scclient.data.model.StreamUrlResponse
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.TrackComment
import com.savoo.scclient.data.model.User

class SoundCloudApi(private val rest: RestClient) {

    suspend fun searchTracks(query: String, limit: Int = 25, linkedPartitioning: Boolean = true): SearchResponse<Track> =
        rest.get("search/tracks", "q" to query, "limit" to limit, "linked_partitioning" to linkedPartitioning)

    suspend fun searchUsers(query: String, limit: Int = 10, linkedPartitioning: Boolean = true): SearchResponse<User> =
        rest.get("search/users", "q" to query, "limit" to limit, "linked_partitioning" to linkedPartitioning)

    suspend fun searchPlaylists(query: String, limit: Int = 10, linkedPartitioning: Boolean = true): SearchResponse<Playlist> =
        rest.get("search/playlists", "q" to query, "limit" to limit, "linked_partitioning" to linkedPartitioning)

    suspend fun getProfileFeed(id: Long, likes: Boolean, next: String?): SearchResponse<ProfileFeedItem> =
        if (next != null) rest.get(next) else rest.get(
            if (likes) "users/$id/likes" else "stream/users/$id/reposts", "limit" to 30,
        )

    suspend fun getProfilePeople(id: Long, following: Boolean, next: String?): SearchResponse<User> =
        if (next != null) rest.get(next) else rest.get("users/$id/${if (following) "followings" else "followers"}", "limit" to 30)

    suspend fun getFollowingIds(id: Long, next: String?): String =
        rest.text(next ?: "users/$id/followings/ids")

    suspend fun setFollowing(targetId: Long, following: Boolean): Int {
        val url = "https://api.soundcloud.com/me/followings/soundcloud:users:$targetId"
        return (if (following) rest.http.put(url) else rest.http.delete(url)).status.value
    }

    suspend fun getMe(): User = rest.get("me")

    suspend fun getUserLikes(id: Long, limit: Int = 200, linkedPartitioning: Boolean = true): SearchResponse<LikeItem> =
        rest.get("users/$id/likes", "limit" to limit, "linked_partitioning" to linkedPartitioning)

    suspend fun getNextLikesPage(url: String): SearchResponse<LikeItem> = rest.get(url)

    suspend fun getNextPage(url: String): SearchResponse<Track> = rest.get(url)

    suspend fun getNextUsersPage(url: String): SearchResponse<User> = rest.get(url)

    suspend fun getNextPlaylistsPage(url: String): SearchResponse<Playlist> = rest.get(url)

    suspend fun resolveStreamUrl(transcodingUrl: String, trackAuthorization: String? = null): StreamUrlResponse =
        rest.get(transcodingUrl, "track_authorization" to trackAuthorization)

    suspend fun getTrack(id: Long): Track = rest.get("tracks/$id")

    suspend fun getUser(id: Long): User = rest.get("users/$id")

    suspend fun getUserTracks(id: Long, limit: Int = 50, offset: Int = 0): SearchResponse<Track> =
        rest.get("users/$id/tracks", "limit" to limit, "offset" to offset)

    suspend fun getRelatedTracks(id: Long, limit: Int = 20, offset: Int = 0): SearchResponse<Track> =
        rest.get("tracks/$id/related", "limit" to limit, "offset" to offset)

    suspend fun getTrackComments(
        id: Long,
        threaded: Int = 0,
        filterReplies: Int = 1,
        limit: Int = 100,
    ): SearchResponse<TrackComment> =
        rest.get("tracks/$id/comments", "threaded" to threaded, "filter_replies" to filterReplies, "limit" to limit)

    suspend fun getUserTopTracks(id: Long, limit: Int = 10): SearchResponse<Track> =
        rest.get("users/$id/toptracks", "limit" to limit)

    suspend fun getUserAlbums(id: Long, limit: Int = 20): SearchResponse<Playlist> =
        rest.get("users/$id/albums", "limit" to limit)

    suspend fun getUserPlaylists(id: Long, limit: Int = 20): SearchResponse<Playlist> =
        rest.get("users/$id/playlists_without_albums", "limit" to limit)

    suspend fun getRelatedArtists(id: Long, limit: Int = 12): SearchResponse<User> =
        rest.get("users/$id/relatedartists", "limit" to limit)

    suspend fun getPlaylist(id: Long): Playlist = rest.get("playlists/$id")

    suspend fun getCharts(kind: String, genre: String, limit: Int = 50, offset: Int = 0): SearchResponse<ChartItem> =
        rest.get("charts", "kind" to kind, "genre" to genre, "limit" to limit, "offset" to offset)
}
