package com.savoo.scclient.data.model

import androidx.room.ColumnInfo
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
    val genre: String? = null,
    @ColumnInfo(defaultValue = "0") val hiddenFromHistory: Boolean = false,
)
