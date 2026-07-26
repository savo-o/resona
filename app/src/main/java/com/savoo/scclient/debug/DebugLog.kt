package com.savoo.scclient.debug

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DebugLog {
    private const val MAX_ENTRIES = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val buffer = ArrayDeque<String>(MAX_ENTRIES)

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries = _entries.asStateFlow()

    private val _verboseNetworkLogging = MutableStateFlow(false)
    val verboseNetworkLogging = _verboseNetworkLogging.asStateFlow()

    fun setVerboseNetworkLogging(enabled: Boolean) {
        _verboseNetworkLogging.value = enabled
        log("DebugLog", "verbose network logging ${if (enabled) "enabled" else "disabled"}")
    }

    @Synchronized
    fun log(tag: String, message: String) {
        Log.d(tag, message)
        val line = "${timeFormat.format(Date())} [$tag] $message"
        if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
        buffer.addLast(line)
        _entries.value = buffer.toList()
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    fun asText(): String = entries.value.joinToString("\n")
}
