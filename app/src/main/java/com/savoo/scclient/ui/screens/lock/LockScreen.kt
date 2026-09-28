package com.savoo.scclient.ui.screens.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.savoo.scclient.R
import com.savoo.scclient.security.AppLockStore
import com.savoo.scclient.security.BiometricKey
import com.savoo.scclient.security.BiometricKeyState
import com.savoo.scclient.security.canUseBiometric
import com.savoo.scclient.security.PinCheckResult
import com.savoo.scclient.security.promptBiometric
import com.savoo.scclient.ui.haptics.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    store: AppLockStore,
    onUnlocked: () -> Unit,
    onDuress: () -> Unit,
) {
    val mode = store.mode.value
    val activity = LocalContext.current as FragmentActivity
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }

    var pin by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var lockedUntil by remember { mutableLongStateOf(store.lockedUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val title = stringResource(R.string.app_lock_title)
    val usePin = stringResource(R.string.app_lock_use_pin)
    var biometricState by remember {
        mutableStateOf(if (mode.usesBiometric) BiometricKey.state() else BiometricKeyState.Unavailable)
    }
    val biometricReady = biometricState is BiometricKeyState.Ready && canUseBiometric(activity)

    fun askBiometric() {
        val cipher = (BiometricKey.state().also { biometricState = it } as? BiometricKeyState.Ready)?.cipher ?: return
        activity.promptBiometric(title = title, negativeText = usePin, cipher = cipher) { ok ->
            if (ok) {
                store.markUnlocked()
                onUnlocked()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (biometricReady) askBiometric()
    }

    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }

    fun submit() {
        if (checking) return
        checking = true
        val entered = pin
        scope.launch {
            when (val result = store.check(entered)) {
                PinCheckResult.Correct -> {
                    if (mode.usesBiometric && biometricState !is BiometricKeyState.Ready) BiometricKey.create()
                    onUnlocked()
                }
                PinCheckResult.Duress -> onDuress()
                is PinCheckResult.Wrong -> {
                    haptics.error()
                    wrong = true
                    lockedUntil = result.lockedUntil
                    pin = ""
                    shake.shake()
                }
                is PinCheckResult.LockedOut -> {
                    lockedUntil = result.lockedUntil
                    pin = ""
                }
            }
            checking = false
        }
    }

    val secondsLeft = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)
    val lockedOut = secondsLeft > 0

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.app_lock_enter_pin),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    lockedOut -> stringResource(R.string.app_lock_too_many_attempts, secondsLeft)
                    wrong -> stringResource(R.string.app_lock_wrong_pin)
                    mode.usesBiometric && biometricState == BiometricKeyState.Invalidated ->
                        stringResource(R.string.app_lock_biometric_changed)
                    else -> " "
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (wrong || lockedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
            PinDots(length = pin.length, shakeOffset = shake.value)
            Spacer(Modifier.height(36.dp))
            PinPad(
                pin = pin,
                onPinChange = {
                    pin = it
                    wrong = false
                },
                onSubmit = ::submit,
                enabled = !checking && !lockedOut,
                leadingAction = if (biometricReady) {
                    {
                        FilledTonalIconButton(
                            onClick = ::askBiometric,
                            modifier = Modifier.size(76.dp),
                        ) {
                            Icon(
                                Icons.Filled.Fingerprint,
                                contentDescription = stringResource(R.string.app_lock_use_biometric),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}
