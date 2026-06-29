package com.savoo.scclient.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_artists")
data class FavoriteArtist(
    @PrimaryKey val artistId: Long,
    val username: String,
    val fullName: String?,
    val avatarUrl: String?,
    val followersCount: Long?,
    val permalinkUrl: String?,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "favorite_playlists")
data class FavoritePlaylist(
    @PrimaryKey val playlistId: Long,
    val title: String,
    val artworkUrl: String?,
    val trackCount: Int,
    val username: String,
    val permalinkUrl: String?,
    val addedAt: Long = System.currentTimeMillis(),
)
