package com.savoo.scclient.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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

// Cover art that is almost pure black, pure white or fully greyscale would collapse every surface
// into one flat tone, so the seed is pulled into a usable saturation/lightness band before any
// surface is derived from it. Used for both the app's surfaces and the Pixel player, which read
// from the very same scheme so the two can never drift apart.
fun Color.asArtworkSeed(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = hsv[1].coerceIn(0.22f, 0.85f)
    hsv[2] = hsv[2].coerceIn(0.45f, 0.85f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// The Pixel look: one artwork-toned scheme shared by the app and the player, with no light/dark
// split - the cover decides the whole palette. The ratios are the ones the player used to derive
// for itself (0.6 toward black for the ground, 0.4 for raised surfaces, 0.4 toward white for the
// accent); the rest of the ladder is spaced around them so cards stay readable against the ground.
fun buildPixelScheme(rawSeed: Color): androidx.compose.material3.ColorScheme {
    val seed = rawSeed.asArtworkSeed()
    val accent = seed.tone(0.4f, Color.White)
    val onSurface = Color(0xFFF7EEF2)
    val secondary = seed.tone(0.25f, Color.White)
    return darkColorScheme(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.5f) Color(0xFF241014) else onSurface,
        primaryContainer = seed.tone(0.35f, Color.Black),
        onPrimaryContainer = onSurface,
        secondary = secondary,
        onSecondary = Color(0xFF241014),
        secondaryContainer = seed.tone(0.42f, Color.Black),
        onSecondaryContainer = onSurface,
        tertiary = seed.tone(0.55f, Color.White),
        onTertiary = Color(0xFF241014),
        tertiaryContainer = seed.tone(0.45f, Color.Black),
        onTertiaryContainer = onSurface,
        background = seed.tone(0.6f, Color.Black),
        onBackground = onSurface,
        surface = seed.tone(0.6f, Color.Black),
        onSurface = onSurface,
        surfaceContainerLowest = seed.tone(0.7f, Color.Black),
        surfaceContainerLow = seed.tone(0.52f, Color.Black),
        surfaceContainer = seed.tone(0.46f, Color.Black),
        surfaceContainerHigh = seed.tone(0.4f, Color.Black),
        surfaceContainerHighest = seed.tone(0.32f, Color.Black),
        surfaceVariant = seed.tone(0.45f, Color.Black),
        onSurfaceVariant = onSurface.copy(alpha = 0.72f),
        outline = onSurface.copy(alpha = 0.4f),
        outlineVariant = lerp(seed.tone(0.4f, Color.Black), onSurface, 0.2f),
    )
}


// The chosen colour is used as the ground exactly as picked, and cards/pills step away from it
// toward white or black depending on how dark it is, so they stay visible on any choice. Accents
// keep coming from the app's own colour theme.
private fun buildCustomBackgroundScheme(background: Color, accentSeed: Color): androidx.compose.material3.ColorScheme {
    val isDarkBg = background.luminance() < 0.5f
    val away = if (isDarkBg) Color.White else Color.Black
    val on = if (isDarkBg) Color(0xFFF4F0F4) else Color(0xFF1B1B1F)
    val base = if (isDarkBg) {
        darkColorScheme(primary = accentSeed.tone(0.35f, Color.White), onPrimary = accentSeed.tone(0.75f, Color.Black))
    } else {
        lightColorScheme(primary = accentSeed, onPrimary = Color.White)
    }
    return base.copy(
        background = background,
        onBackground = on,
        surface = background,
        onSurface = on,
        surfaceContainerLowest = background.tone(0.04f, if (isDarkBg) Color.Black else Color.White),
        surfaceContainerLow = background.tone(0.06f, away),
        surfaceContainer = background.tone(0.1f, away),
        surfaceContainerHigh = background.tone(0.14f, away),
        surfaceContainerHighest = background.tone(0.19f, away),
        surfaceVariant = background.tone(0.12f, away),
        onSurfaceVariant = on.copy(alpha = 0.72f),
        outline = on.copy(alpha = 0.4f),
        outlineVariant = lerp(background.tone(0.14f, away), on, 0.2f),
    )
}

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResonaTheme(
    colorTheme: AppColorTheme = AppColorTheme.ORANGE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    overrideSeedColor: Color? = null,
    tintSurfaces: Boolean = false,
    customBackground: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val targetScheme = when {
        customBackground != null -> buildCustomBackgroundScheme(
            customBackground,
            overrideSeedColor ?: colorTheme.seedPrimary,
        )
        overrideSeedColor != null && tintSurfaces -> buildPixelScheme(overrideSeedColor)
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
        background = animateColorAsState(targetScheme.background, animationSpec = animSpec).value,
        surface = animateColorAsState(targetScheme.surface, animationSpec = animSpec).value,
        surfaceContainerLowest = animateColorAsState(targetScheme.surfaceContainerLowest, animationSpec = animSpec).value,
        surfaceContainerLow = animateColorAsState(targetScheme.surfaceContainerLow, animationSpec = animSpec).value,
        surfaceContainer = animateColorAsState(targetScheme.surfaceContainer, animationSpec = animSpec).value,
        surfaceContainerHigh = animateColorAsState(targetScheme.surfaceContainerHigh, animationSpec = animSpec).value,
        surfaceContainerHighest = animateColorAsState(targetScheme.surfaceContainerHighest, animationSpec = animSpec).value,
        outlineVariant = animateColorAsState(targetScheme.outlineVariant, animationSpec = animSpec).value,
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = rememberSCTypography(),
        content = content
    )
}
