package com.savoo.scclient.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.savoo.scclient.data.repository.HapticsIntensity

val LocalHapticsEnabled = staticCompositionLocalOf { true }
val LocalHapticsIntensity = staticCompositionLocalOf { HapticsIntensity.MEDIUM }

private fun Context.vibratorService(): Vibrator =
    if (Build.VERSION.SDK_INT >= 31) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

private fun HapticsIntensity.multiplier(): Float = when (this) {
    HapticsIntensity.LOW -> 0.55f
    HapticsIntensity.MEDIUM -> 1f
    HapticsIntensity.HIGH -> 1.55f
}

class Haptics internal constructor(
    private val vibrator: Vibrator,
    private val enabled: Boolean,
    private val intensity: HapticsIntensity,
) {
    fun tick() = fire(12L, 90)
    fun click() = fire(18L, 150)
    fun seekTick() = fire(8L, 70)
    fun seekEdge() = fire(16L, 180)
    fun longPress() = fire(28L, 220)

    private fun fire(durationMs: Long, amplitude: Int) {
        if (!enabled) return
        val scaled = (amplitude * intensity.multiplier()).toInt().coerceIn(1, 255)
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, scaled))
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    val enabled = LocalHapticsEnabled.current
    val intensity = LocalHapticsIntensity.current
    return remember(enabled, intensity) { Haptics(context.vibratorService(), enabled, intensity) }
}

@Composable
fun rememberHapticTick(): () -> Unit {
    val haptics = rememberHaptics()
    return { haptics.tick() }
}
