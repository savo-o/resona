package com.savoo.scclient.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_playlists")
data class LocalPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "local_playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class LocalPlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val title: String,
    val username: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val permalinkUrl: String?,
    val userId: Long,
    val userAvatarUrl: String?,
    val genre: String? = null,
)

data class LocalPlaylistSummary(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
    val artworkUrl: String?,
)

fun localPlaylistRouteId(localId: Long): Long = -localId

fun isLocalPlaylistRouteId(routeId: Long): Boolean = routeId < 0

fun LocalPlaylistSummary.toFavoritePlaylist(label: String) = FavoritePlaylist(
    playlistId = localPlaylistRouteId(id),
    title = title,
    artworkUrl = artworkUrl,
    trackCount = trackCount,
    username = label,
    permalinkUrl = null,
    addedAt = updatedAt,
)

fun Playlist.toFavoritePlaylist(label: String) = FavoritePlaylist(
    playlistId = id,
    title = title,
    artworkUrl = displayArtworkUrl,
    trackCount = trackCount,
    username = label,
    permalinkUrl = permalinkUrl,
)

fun LocalPlaylistTrack.toTrack() = Track(
    id = trackId,
    title = title,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    user = User(id = userId, username = username, avatarUrl = userAvatarUrl),
    permalinkUrl = permalinkUrl,
    genre = genre,
)

fun Track.toLocalPlaylistTrack(playlistId: Long, position: Int) = LocalPlaylistTrack(
    playlistId = playlistId,
    trackId = id,
    position = position,
    title = title,
    username = user.username,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    permalinkUrl = permalinkUrl,
    userId = user.id,
    userAvatarUrl = user.avatarUrl,
    genre = genre,
)
