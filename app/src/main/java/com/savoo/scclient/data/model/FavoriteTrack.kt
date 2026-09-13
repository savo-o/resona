package com.savoo.scclient.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(
    tableName = "favorites",
    indices = [
        Index("addedAt"),
        Index("titleKey"),
        Index("artistKey"),
        Index("durationMs"),
    ],
)
data class FavoriteTrack(
    @PrimaryKey val trackId: Long,
    val title: String,
    val username: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val permalinkUrl: String?,
    val userId: Long,
    val userAvatarUrl: String?,
    val addedAt: Long = System.currentTimeMillis(),
    // "LOCAL" (favorited only in-app), "ONLINE" (mirrors a SoundCloud like, not separately
    // favorited locally), or "BOTH". See FavoriteSource in ui/components/TrackRow.kt.
    val source: String = "LOCAL",
    val genre: String? = null,
    @ColumnInfo(defaultValue = "") val titleKey: String = favoriteTextKey(title),
    @ColumnInfo(defaultValue = "") val artistKey: String = favoriteTextKey(username),
)

fun favoriteTextKey(text: String): String = text.lowercase(Locale.ROOT).replace('ё', 'е')

fun FavoriteTrack.toTrack() = Track(
    id = trackId,
    title = title,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    user = User(id = userId, username = username, avatarUrl = userAvatarUrl),
    permalinkUrl = permalinkUrl,
    genre = genre,
)
