package com.savoo.scclient.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.savoo.scclient.R
import com.savoo.scclient.security.AppLockMode
import com.savoo.scclient.security.AppLockStore
import com.savoo.scclient.security.BiometricKey
import com.savoo.scclient.security.BiometricKeyState
import com.savoo.scclient.security.canUseBiometric
import com.savoo.scclient.security.promptBiometric
import com.savoo.scclient.ui.components.SwitchItem
import com.savoo.scclient.ui.haptics.rememberHapticTick
import com.savoo.scclient.ui.haptics.rememberHaptics
import com.savoo.scclient.ui.screens.lock.PinDots
import com.savoo.scclient.ui.screens.lock.PinPad
import com.savoo.scclient.ui.screens.lock.shake
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    val store: AppLockStore,
) : ViewModel() {
    init {
        store.load()
    }
}

private enum class PinTarget { MAIN, DURESS }

private data class PinRequest(val target: PinTarget, val thenMode: AppLockMode?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    viewModel: AppLockViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val store = viewModel.store
    val mode by store.mode.collectAsState()
    val duressEnabled by store.duressEnabled.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val haptic = rememberHapticTick()
    val biometricAvailable = remember { canUseBiometric(context) }

    var pinRequest by remember { mutableStateOf<PinRequest?>(null) }
    var pendingEnable by remember { mutableStateOf<AppLockMode?>(null) }
    var showDuressWarning by remember { mutableStateOf(false) }

    val biometricTitle = stringResource(R.string.app_lock_confirm_biometric)
    val cancel = stringResource(R.string.cancel)

    fun applyMode(target: AppLockMode) {
        if (target != AppLockMode.OFF && !store.hasPin()) {
            pinRequest = PinRequest(PinTarget.MAIN, target)
        } else {
            store.setMode(target)
        }
    }

    fun enable(target: AppLockMode) {
        if (target.usesBiometric && !mode.usesBiometric) {
            val cipher = (BiometricKey.takeIf { it.create() }?.state() as? BiometricKeyState.Ready)?.cipher ?: return
            activity?.promptBiometric(title = biometricTitle, negativeText = cancel, cipher = cipher) { ok ->
                if (ok) applyMode(target) else if (!mode.usesBiometric) BiometricKey.delete()
            }
        } else {
            applyMode(target)
        }
    }

    fun select(target: AppLockMode) {
        if (target == mode) return
        haptic()
        if (mode == AppLockMode.OFF) pendingEnable = target else enable(target)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_lock_settings_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            SettingsSectionCard(title = stringResource(R.string.app_lock_method)) {
                ModeOption(
                    title = stringResource(R.string.app_lock_mode_off),
                    subtitle = null,
                    selected = mode == AppLockMode.OFF,
                    enabled = true,
                    onClick = { select(AppLockMode.OFF) },
                )
                ModeOption(
                    title = stringResource(R.string.app_lock_mode_pin),
                    subtitle = null,
                    selected = mode == AppLockMode.PIN,
                    enabled = true,
                    onClick = { select(AppLockMode.PIN) },
                )
                ModeOption(
                    title = stringResource(R.string.app_lock_mode_biometric_or_pin),
                    subtitle = if (biometricAvailable) null else stringResource(R.string.app_lock_biometric_unavailable),
                    selected = mode == AppLockMode.BIOMETRIC_OR_PIN,
                    enabled = biometricAvailable || mode == AppLockMode.BIOMETRIC_OR_PIN,
                    onClick = { select(AppLockMode.BIOMETRIC_OR_PIN) },
                )
            }

            if (mode != AppLockMode.OFF) {
                SettingsSectionCard {
                    NavRow(stringResource(R.string.app_lock_change_pin)) {
                        haptic()
                        pinRequest = PinRequest(PinTarget.MAIN, null)
                    }
                }

                SettingsSectionCard(title = stringResource(R.string.app_lock_duress_title)) {
                    SwitchItem(
                        title = stringResource(R.string.app_lock_duress_switch),
                        subtitle = stringResource(R.string.app_lock_duress_desc),
                        checked = duressEnabled,
                        onCheckedChange = { enable ->
                            if (enable) showDuressWarning = true else store.clearDuress()
                        },
                    )
                    if (duressEnabled) {
                        SettingsDivider()
                        NavRow(stringResource(R.string.app_lock_duress_change)) {
                            haptic()
                            pinRequest = PinRequest(PinTarget.DURESS, null)
                        }
                    }
                }
            }

            if (mode != AppLockMode.OFF) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.app_lock_cold_start_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDuressWarning) {
        AlertDialog(
            onDismissRequest = { showDuressWarning = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.app_lock_duress_title)) },
            text = { Text(stringResource(R.string.app_lock_duress_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showDuressWarning = false
                    pinRequest = PinRequest(PinTarget.DURESS, null)
                }) { Text(stringResource(R.string.app_lock_duress_set)) }
            },
            dismissButton = {
                TextButton(onClick = { showDuressWarning = false }) { Text(cancel) }
            },
        )
    }

    pendingEnable?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingEnable = null },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.app_lock_enable_warning_title)) },
            text = { Text(stringResource(R.string.app_lock_enable_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingEnable = null
                    enable(target)
                }) { Text(stringResource(R.string.app_lock_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingEnable = null }) { Text(cancel) }
            },
        )
    }

    pinRequest?.let { request ->
        PinSetupDialog(
            target = request.target,
            store = store,
            onDismiss = { pinRequest = null },
            onSaved = {
                pinRequest = null
                request.thenMode?.let(store::setMode)
            },
        )
    }
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Password,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinSetupDialog(
    target: PinTarget,
    store: AppLockStore,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val shake = remember { Animatable(0f) }
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun fail(message: Int) {
        error = message
        pin = ""
        haptics.error()
        scope.launch { shake.shake() }
    }

    fun submit() {
        if (busy) return
        val entered = pin
        val confirmed = first
        if (confirmed == null) {
            busy = true
            scope.launch {
                val clash = when (target) {
                    PinTarget.MAIN -> store.matchesDuressPin(entered)
                    PinTarget.DURESS -> store.matchesPin(entered)
                }
                busy = false
                if (clash) {
                    fail(if (target == PinTarget.MAIN) R.string.app_lock_pin_clash_duress else R.string.app_lock_pin_clash_main)
                } else {
                    first = entered
                    pin = ""
                    error = null
                }
            }
        } else if (entered != confirmed) {
            first = null
            fail(R.string.app_lock_pin_mismatch)
        } else {
            busy = true
            scope.launch {
                when (target) {
                    PinTarget.MAIN -> store.setPin(entered)
                    PinTarget.DURESS -> store.setDuressPin(entered)
                }
                haptics.success()
                onSaved()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                IconButton(onClick = onDismiss, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(
                            when {
                                first != null -> R.string.app_lock_repeat_pin
                                target == PinTarget.DURESS -> R.string.app_lock_new_duress_pin
                                else -> R.string.app_lock_new_pin
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error?.let { stringResource(it) } ?: stringResource(R.string.app_lock_pin_length_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    PinDots(length = pin.length, shakeOffset = shake.value)
                    Spacer(Modifier.height(36.dp))
                    PinPad(
                        pin = pin,
                        onPinChange = { pin = it },
                        onSubmit = ::submit,
                        enabled = !busy,
                    )
                }
            }
        }
    }
}
