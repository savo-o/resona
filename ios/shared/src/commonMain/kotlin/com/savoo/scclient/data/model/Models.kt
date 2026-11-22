package com.savoo.scclient.data.model

import com.savoo.scclient.compat.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: Long,
    val title: String = "",
    @SerialName("duration") val durationMs: Long = 0L,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("user") val user: User = User(),
    @SerialName("playback_count") val playbackCount: Long? = null,
    @SerialName("likes_count") val likesCount: Long? = null,
    @SerialName("genre") val genre: String? = null,
    @SerialName("media") val media: Media? = null,
    @SerialName("track_authorization") val trackAuthorization: String? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    @SerialName("policy") val policy: String? = null,
    @SerialName("waveform_url") val waveformUrl: String? = null,
)

@Serializable
data class Media(
    val transcodings: List<Transcoding> = emptyList()
)

@Serializable
data class Transcoding(
    val url: String,
    val format: TranscodingFormat,
    val quality: String? = null,
    val snipped: Boolean? = null
)

@Serializable
data class TranscodingFormat(
    @SerialName("protocol") val protocol: String,
    @SerialName("mime_type") val mimeType: String
)

@Serializable
data class StreamUrlResponse(
    val url: String
)

@Serializable
data class User(
    val id: Long = 0,
    val username: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("followers_count") val followersCount: Long? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("track_count") val trackCount: Int? = null,
    @SerialName("verified") val verified: Boolean? = null,
)

@Serializable
data class TrackComment(
    val id: Long = 0,
    val body: String = "",
    @SerialName("timestamp") val timestampMs: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("user") val user: User = User(),
)

@Serializable
data class SearchResponse<T>(
    val collection: List<T>,
    @SerialName("next_href") val nextHref: String?
)

/** One entry of `users/{id}/likes` - each item wraps either a liked track or a liked playlist,
 * distinguished by [kind] ("like" for both; only one of [track]/[playlist] is present). */
@Serializable
data class LikeItem(
    val kind: String = "",
    val track: Track? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String?,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("scope") val scope: String?
)

@Serializable
data class ChartItem(
    val score: Double? = null,
    @SerialName("track") val track: Track = Track(id = 0),
)

@Serializable
data class Playlist(
    val id: Long,
    val title: String,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("user") val user: User,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("tracks") val tracks: List<Track>? = null,
    @SerialName("is_album") val isAlbum: Boolean? = null,
    @SerialName("set_type") val setType: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("duration") val durationMs: Long? = null,
    @SerialName("likes_count") val likesCount: Long? = null,
    @SerialName("sharing") val sharing: String? = null,
)

val Playlist.displayArtworkUrl: String?
    get() = artworkUrl ?: tracks?.firstNotNullOfOrNull { it.artworkUrl }

val Playlist.releaseYear: String?
    get() = (releaseDate ?: createdAt)?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
