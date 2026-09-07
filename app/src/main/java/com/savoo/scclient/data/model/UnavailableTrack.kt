package com.savoo.scclient.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UnavailableReason { DELETED, DRM }

@Entity(tableName = "unavailable_tracks")
data class UnavailableTrackEntity(
    @PrimaryKey val trackId: Long,
    val reason: UnavailableReason,
    val markedAt: Long,
)
