package com.savoo.scclient.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

private fun Color.tone(amount: Float, towards: Color): Color = lerp(this, towards, amount)

private fun buildLightScheme(theme: AppColorTheme) = lightColorScheme(
    primary = theme.seedPrimary,
    onPrimary = Color.White,
    primaryContainer = theme.seedPrimary.tone(0.9f, Color.White),
    onPrimaryContainer = theme.seedPrimary.tone(0.3f, Color.Black),
    secondary = theme.seedSecondary,
    onSecondary = Color.White,
    secondaryContainer = theme.seedSecondary.tone(0.9f, Color.White),
    onSecondaryContainer = theme.seedSecondary.tone(0.3f, Color.Black),
    tertiary = theme.seedTertiary,
    onTertiary = Color.White,
    tertiaryContainer = theme.seedTertiary.tone(0.9f, Color.White),
    onTertiaryContainer = theme.seedTertiary.tone(0.3f, Color.Black),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = theme.seedPrimary.tone(0.93f, Color.White),
    onSurfaceVariant = theme.seedPrimary.tone(0.35f, Color.Black),
)

private fun buildDarkScheme(theme: AppColorTheme) = darkColorScheme(
    primary = theme.seedPrimary.tone(0.35f, Color.White),
    onPrimary = theme.seedPrimary.tone(0.75f, Color.Black),
    primaryContainer = theme.seedPrimary.tone(0.55f, Color.Black),
    onPrimaryContainer = theme.seedPrimary.tone(0.85f, Color.White),
    secondary = theme.seedSecondary.tone(0.35f, Color.White),
    onSecondary = theme.seedSecondary.tone(0.75f, Color.Black),
    secondaryContainer = theme.seedSecondary.tone(0.55f, Color.Black),
    onSecondaryContainer = theme.seedSecondary.tone(0.85f, Color.White),
    tertiary = theme.seedTertiary.tone(0.35f, Color.White),
    onTertiary = theme.seedTertiary.tone(0.75f, Color.Black),
    tertiaryContainer = theme.seedTertiary.tone(0.55f, Color.Black),
    onTertiaryContainer = theme.seedTertiary.tone(0.85f, Color.White),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceVariant = theme.seedPrimary.tone(0.65f, Color.Black),
    onSurfaceVariant = theme.seedPrimary.tone(0.85f, Color.White),
)

private fun buildSeedScheme(seed: Color, dark: Boolean): androidx.compose.material3.ColorScheme {
    val secondary = seed.tone(0.7f, if (dark) Color.Black else Color.White)
    val tertiary = seed.tone(0.5f, if (dark) Color.Black else Color.White)
    return if (dark) darkColorScheme(
        primary = seed.tone(0.35f, Color.White),
        onPrimary = seed.tone(0.75f, Color.Black),
        primaryContainer = seed.tone(0.55f, Color.Black),
        onPrimaryContainer = seed.tone(0.85f, Color.White),
        secondary = secondary.tone(0.35f, Color.White),
        onSecondary = secondary.tone(0.75f, Color.Black),
        secondaryContainer = secondary.tone(0.55f, Color.Black),
        onSecondaryContainer = secondary.tone(0.85f, Color.White),
        tertiary = tertiary.tone(0.35f, Color.White),
        onTertiary = tertiary.tone(0.75f, Color.Black),
        tertiaryContainer = tertiary.tone(0.55f, Color.Black),
        onTertiaryContainer = tertiary.tone(0.85f, Color.White),
        background = Color(0xFF141218),
        surface = Color(0xFF141218),
        surfaceVariant = seed.tone(0.65f, Color.Black),
        onSurfaceVariant = seed.tone(0.85f, Color.White),
    ) else lightColorScheme(
        primary = seed,
        onPrimary = Color.White,
        primaryContainer = seed.tone(0.9f, Color.White),
        onPrimaryContainer = seed.tone(0.3f, Color.Black),
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = secondary.tone(0.9f, Color.White),
        onSecondaryContainer = secondary.tone(0.3f, Color.Black),
        tertiary = tertiary,
        onTertiary = Color.White,
        tertiaryContainer = tertiary.tone(0.9f, Color.White),
        onTertiaryContainer = tertiary.tone(0.3f, Color.Black),
        background = Color(0xFFFFFBFE),
        surface = Color(0xFFFFFBFE),
        surfaceVariant = seed.tone(0.93f, Color.White),
        onSurfaceVariant = seed.tone(0.35f, Color.Black),
    )
}

@Composable
fun ResonaTheme(
    colorTheme: AppColorTheme = AppColorTheme.ORANGE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    overrideSeedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val targetScheme = when {
        overrideSeedColor != null -> buildSeedScheme(overrideSeedColor, darkTheme)
        colorTheme == AppColorTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> buildDarkScheme(colorTheme)
        else -> buildLightScheme(colorTheme)
    }

    val animSpec = spring<Color>(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
    val colorScheme = targetScheme.copy(
        primary = animateColorAsState(targetScheme.primary, animationSpec = animSpec).value,
        onPrimary = animateColorAsState(targetScheme.onPrimary, animationSpec = animSpec).value,
        primaryContainer = animateColorAsState(targetScheme.primaryContainer, animationSpec = animSpec).value,
        onPrimaryContainer = animateColorAsState(targetScheme.onPrimaryContainer, animationSpec = animSpec).value,
        secondary = animateColorAsState(targetScheme.secondary, animationSpec = animSpec).value,
        secondaryContainer = animateColorAsState(targetScheme.secondaryContainer, animationSpec = animSpec).value,
        tertiary = animateColorAsState(targetScheme.tertiary, animationSpec = animSpec).value,
        tertiaryContainer = animateColorAsState(targetScheme.tertiaryContainer, animationSpec = animSpec).value,
        surfaceVariant = animateColorAsState(targetScheme.surfaceVariant, animationSpec = animSpec).value,
        onSurfaceVariant = animateColorAsState(targetScheme.onSurfaceVariant, animationSpec = animSpec).value,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SCTypography,
        content = content
    )
}
