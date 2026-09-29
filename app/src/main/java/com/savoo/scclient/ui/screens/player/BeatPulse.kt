package com.savoo.scclient.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.savoo.scclient.player.SoundEffects

private const val OUTPUT_LATENCY_NANOS = 60_000_000L
private const val ATTACK = 0.28f
private const val RELEASE = 0.055f

@Composable
fun rememberBeatPulse(effects: SoundEffects, enabled: Boolean, isPlaying: Boolean): () -> Float {
    val pulse: MutableFloatState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled, isPlaying) {
        if (!enabled || !isPlaying) {
            while (pulse.floatValue > 0.001f) {
                withFrameNanos { }
                pulse.floatValue *= 0.85f
            }
            pulse.floatValue = 0f
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { }
            val target = effects.beatLevelAt(System.nanoTime() - OUTPUT_LATENCY_NANOS)
            val current = pulse.floatValue
            pulse.floatValue = current + (target - current) * if (target > current) ATTACK else RELEASE
        }
    }
    return remember(pulse) { { pulse.floatValue } }
}
