package com.savoo.scclient.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_tracks")
data class OfflineTrack(
    @PrimaryKey val trackId: Long,
    val title: String,
    val username: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val permalinkUrl: String?,
    val userId: Long,
    val userAvatarUrl: String?,
    val localPath: String,
    val savedAt: Long = System.currentTimeMillis(),
    val fileSizeBytes: Long = 0,
)
