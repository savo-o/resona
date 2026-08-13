package com.savoo.scclient.data.remote

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ConnectivityEventBus {
    private val _unreachableTick = MutableStateFlow(0)
    val unreachableTick = _unreachableTick.asStateFlow()

    private val _restoredTick = MutableStateFlow(0)
    val restoredTick = _restoredTick.asStateFlow()

    private val isUnreachable = AtomicBoolean(false)

    fun notifyUnreachable() {
        isUnreachable.set(true)
        _unreachableTick.update { it + 1 }
    }

    fun notifyReachable() {
        if (isUnreachable.compareAndSet(true, false)) {
            _restoredTick.update { it + 1 }
        }
    }
}
