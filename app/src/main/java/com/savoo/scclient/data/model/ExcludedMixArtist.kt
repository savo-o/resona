package com.savoo.scclient.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "excluded_mix_artists")
data class ExcludedMixArtist(
    @PrimaryKey val artistId: Long,
    val username: String,
    val excludedAt: Long = System.currentTimeMillis(),
)
