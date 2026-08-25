package com.savoo.scclient.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class TrackSelectionState {
    var selectedIds by mutableStateOf(emptySet<Long>())
        private set

    val isActive: Boolean get() = selectedIds.isNotEmpty()
    val count: Int get() = selectedIds.size

    fun contains(trackId: Long): Boolean = trackId in selectedIds

    fun toggle(trackId: Long) {
        selectedIds = if (trackId in selectedIds) selectedIds - trackId else selectedIds + trackId
    }

    fun selectAll(ids: Collection<Long>) {
        selectedIds = if (selectedIds.containsAll(ids)) emptySet() else ids.toSet()
    }

    fun clear() {
        selectedIds = emptySet()
    }
}

@Composable
fun rememberTrackSelection(): TrackSelectionState {
    val state = remember { TrackSelectionState() }
    BackHandler(enabled = state.isActive) { state.clear() }
    return state
}
