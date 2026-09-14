package com.savoo.scclient.data.model

import androidx.room.Entity

@Entity(tableName = "lyrics_sync", primaryKeys = ["trackId", "provider"])
data class LyricsSyncEntity(
    val trackId: Long,
    val provider: String,
    val offsetMs: Long,
    val driftMsPerMin: Long,
    val title: String? = null,
    val artist: String? = null,
    val trackDurationMs: Long? = null,
    val lyricsDurationMs: Long? = null,
    val lastLineMs: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
