package com.savoo.scclient.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ConnectivityEventBus {
    private val _unreachableTick = MutableStateFlow(0)
    val unreachableTick = _unreachableTick.asStateFlow()

    fun notifyUnreachable() {
        _unreachableTick.update { it + 1 }
    }
}
