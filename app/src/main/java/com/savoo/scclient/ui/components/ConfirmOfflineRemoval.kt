package com.savoo.scclient.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.savoo.scclient.ui.haptics.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ConfirmWindowMs = 3000L

@Composable
fun ConfirmOfflineRemoval(
    onRemove: () -> Unit,
    content: @Composable (armed: Boolean, onPress: () -> Unit, modifier: Modifier) -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val currentOnRemove by rememberUpdatedState(onRemove)
    var armed by remember { mutableStateOf(false) }

    LaunchedEffect(armed) {
        if (armed) {
            delay(ConfirmWindowMs)
            armed = false
        }
    }

    val onPress: () -> Unit = {
        if (armed) {
            armed = false
            haptics.remove()
            currentOnRemove()
        } else {
            armed = true
            haptics.warn()
            scope.launch {
                scale.snapTo(0.75f)
                scale.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                )
            }
        }
    }

    content(armed, onPress, Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
}
