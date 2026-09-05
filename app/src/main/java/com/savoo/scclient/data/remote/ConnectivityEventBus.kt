package com.savoo.scclient.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ConnectivityEventBus {
    private val _unreachableTick = MutableStateFlow(0)
    val unreachableTick = _unreachableTick.asStateFlow()

    private val _restoredTick = MutableStateFlow(0)
    val restoredTick = _restoredTick.asStateFlow()

    private val _isUnreachable = MutableStateFlow(false)
    val isUnreachable = _isUnreachable.asStateFlow()

    fun notifyUnreachable() {
        if (_isUnreachable.compareAndSet(expect = false, update = true)) {
            _unreachableTick.update { it + 1 }
        }
    }

    fun notifyReachable() {
        if (_isUnreachable.compareAndSet(expect = true, update = false)) {
            _restoredTick.update { it + 1 }
        }
    }
}
