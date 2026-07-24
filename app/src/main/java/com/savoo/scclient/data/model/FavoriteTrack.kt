package com.savoo.scclient.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
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
)
