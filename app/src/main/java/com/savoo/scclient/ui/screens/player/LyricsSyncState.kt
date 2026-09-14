package com.savoo.scclient.ui.screens.player

import com.savoo.scclient.data.model.LyricsSync

data class LyricsSyncState(
    val sync: LyricsSync = LyricsSync(),
    val automatic: LyricsSync = LyricsSync(),
)

sealed interface LyricsSyncAction {
    data class AdjustOffset(val deltaMs: Long) : LyricsSyncAction
    data object ResetOffset : LyricsSyncAction
    data class AdjustDrift(val deltaMsPerMin: Long) : LyricsSyncAction
    data object ResetDrift : LyricsSyncAction
}
