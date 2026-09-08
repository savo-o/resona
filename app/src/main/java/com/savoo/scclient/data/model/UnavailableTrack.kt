package com.savoo.scclient.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UnavailableReason { DELETED, DRM, PREVIEW }

@Entity(tableName = "unavailable_tracks")
data class UnavailableTrackEntity(
    @PrimaryKey val trackId: Long,
    val reason: UnavailableReason,
    val markedAt: Long,
)

fun Track.restrictionReason(): UnavailableReason? = when {
    policy.equals("SNIP", ignoreCase = true) -> UnavailableReason.PREVIEW
    policy.equals("BLOCK", ignoreCase = true) -> UnavailableReason.DRM
    media?.transcodings?.isNotEmpty() == true && media.transcodings.all { it.snipped == true } ->
        UnavailableReason.PREVIEW
    else -> null
}
