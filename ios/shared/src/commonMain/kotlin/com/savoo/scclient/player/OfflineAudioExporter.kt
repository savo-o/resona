package com.savoo.scclient.player

import com.savoo.scclient.compat.File
import com.savoo.scclient.data.model.OfflineTrack

internal expect suspend fun exportOfflineAudio(
    track: OfflineTrack,
    source: File,
    destination: File,
    onProgress: (Float) -> Unit,
)
