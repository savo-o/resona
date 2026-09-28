package com.savoo.scclient.ui.screens.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.security.MaxPinLength
import com.savoo.scclient.security.MinPinLength
import com.savoo.scclient.ui.haptics.rememberHapticTick
import kotlin.math.roundToInt

suspend fun Animatable<Float, *>.shake() {
    for (target in listOf(18f, -16f, 12f, -8f, 4f, 0f)) {
        animateTo(target, spring(stiffness = 4000f))
    }
}

@Composable
fun PinDots(
    length: Int,
    shakeOffset: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(20.dp)
            .offset { IntOffset(shakeOffset.roundToInt(), 0) },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val visible = maxOf(length, MinPinLength)
        repeat(visible) { index ->
            val filled = index < length
            val scale by animateFloatAsState(if (filled) 1f else 0.6f, label = "pinDot")
            Box(
                Modifier
                    .size(14.dp)
                    .scale(scale)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
fun PinPad(
    pin: String,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    leadingAction: (@Composable () -> Unit)? = null,
) {
    val haptic = rememberHapticTick()
    val keySize = 76.dp
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit ->
                    DigitKey(digit.toString(), keySize, enabled) {
                        if (pin.length < MaxPinLength) {
                            haptic()
                            onPinChange(pin + digit)
                        }
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(keySize), contentAlignment = Alignment.Center) {
                if (pin.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = { haptic(); onPinChange(pin.dropLast(1)) },
                        enabled = enabled,
                        modifier = Modifier.size(keySize),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.app_lock_backspace))
                    }
                } else {
                    leadingAction?.invoke()
                }
            }
            DigitKey("0", keySize, enabled) {
                if (pin.length < MaxPinLength) {
                    haptic()
                    onPinChange(pin + "0")
                }
            }
            FilledIconButton(
                onClick = { haptic(); onSubmit() },
                enabled = enabled && pin.length >= MinPinLength,
                modifier = Modifier.size(keySize),
            ) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.app_lock_confirm))
            }
        }
    }
}

@Composable
private fun DigitKey(digit: String, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Text(digit, style = MaterialTheme.typography.headlineMedium)
    }
}
