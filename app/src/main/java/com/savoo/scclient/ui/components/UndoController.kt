package com.savoo.scclient.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class UndoAction(
    @StringRes val messageRes: Int,
    val messageArgs: List<Any> = emptyList(),
    val icon: ImageVector? = null,
    val onUndo: suspend () -> Unit,
    val id: Long = System.nanoTime(),
)

@Singleton
class UndoController @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _current = MutableStateFlow<UndoAction?>(null)
    val current = _current.asStateFlow()

    fun show(action: UndoAction) {
        _current.value = action
    }

    fun undo(action: UndoAction) {
        if (_current.compareAndSet(action, null)) scope.launch { action.onUndo() }
    }

    fun dismiss(action: UndoAction) {
        _current.compareAndSet(action, null)
    }
}
