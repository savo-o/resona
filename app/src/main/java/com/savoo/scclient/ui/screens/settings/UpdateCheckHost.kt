package com.savoo.scclient.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.UpdateCheckResult
import com.savoo.scclient.data.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateAutoCheckViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    private val _available = MutableStateFlow<UpdateCheckResult.Available?>(null)
    val available = _available.asStateFlow()

    private var checkedThisSession = false

    fun checkIfNeeded() {
        if (checkedThisSession) return
        checkedThisSession = true
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.autoCheckUpdates) return@launch
            // Automatic checks stay silent unless there's actually something to show.
            val result = updateRepository.checkForUpdate(settings.updateChannel)
            if (result is UpdateCheckResult.Available) {
                _available.value = result
            }
        }
    }

    fun dismiss() {
        _available.value = null
    }
}

/** Drop into the composition once near the app root - silently checks for updates on launch
 * (if enabled in Settings) and surfaces [UpdateAvailableDialog] only when one is actually found. */
@Composable
fun UpdateCheckHost(viewModel: UpdateAutoCheckViewModel = hiltViewModel()) {
    val available by viewModel.available.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkIfNeeded() }

    available?.let { result ->
        UpdateAvailableDialog(
            result = result,
            onDismiss = { viewModel.dismiss() },
        )
    }
}
