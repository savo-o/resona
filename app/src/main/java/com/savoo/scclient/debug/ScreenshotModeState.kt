package com.savoo.scclient.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenshotModeState {
    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        _enabled.value = value
    }
}
