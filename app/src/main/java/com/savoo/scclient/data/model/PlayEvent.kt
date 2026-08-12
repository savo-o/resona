package com.savoo.scclient.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val artworkUrl: String?,
    val msPlayed: Long,
    val playedAt: Long,
)
