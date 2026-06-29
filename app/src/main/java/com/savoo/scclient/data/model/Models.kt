package com.savoo.scclient.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Track(
    val id: Long,
    val title: String = "",
    @Json(name = "duration") val durationMs: Long = 0L,
    @Json(name = "artwork_url") val artworkUrl: String? = null,
    @Json(name = "user") val user: User = User(),
    @Json(name = "playback_count") val playbackCount: Long? = null,
    @Json(name = "likes_count") val likesCount: Long? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "media") val media: Media? = null,
    @Json(name = "permalink_url") val permalinkUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class Media(
    val transcodings: List<Transcoding> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Transcoding(
    val url: String,
    val format: TranscodingFormat,
    val quality: String? = null
)

@JsonClass(generateAdapter = true)
data class TranscodingFormat(
    @Json(name = "protocol") val protocol: String,
    @Json(name = "mime_type") val mimeType: String
)

@JsonClass(generateAdapter = true)
data class StreamUrlResponse(
    val url: String
)

@JsonClass(generateAdapter = true)
data class User(
    val id: Long = 0,
    val username: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "followers_count") val followersCount: Long? = null,
    @Json(name = "full_name") val fullName: String? = null,
    @Json(name = "permalink_url") val permalinkUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class SearchResponse<T>(
    val collection: List<T>,
    @Json(name = "next_href") val nextHref: String?
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String?,
    @Json(name = "expires_in") val expiresIn: Long,
    @Json(name = "scope") val scope: String?
)

@JsonClass(generateAdapter = true)
data class Playlist(
    val id: Long,
    val title: String,
    @Json(name = "artwork_url") val artworkUrl: String? = null,
    @Json(name = "track_count") val trackCount: Int = 0,
    @Json(name = "user") val user: User,
    @Json(name = "permalink_url") val permalinkUrl: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "tracks") val tracks: List<Track>? = null,
)
