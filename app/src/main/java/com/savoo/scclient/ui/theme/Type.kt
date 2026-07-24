package com.savoo.scclient.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.savoo.scclient.R

private fun typefaceWithCyrillicFallback(context: Context, primaryResId: Int, fallbackResId: Int): Typeface {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val primary = PlatformFontFamily.Builder(PlatformFont.Builder(context.resources, primaryResId).build()).build()
            val fallback = PlatformFontFamily.Builder(PlatformFont.Builder(context.resources, fallbackResId).build()).build()
            return Typeface.CustomFallbackBuilder(primary).addCustomFallback(fallback).build()
        } catch (_: Exception) {
        }
    }
    return ResourcesCompat.getFont(context, primaryResId) ?: Typeface.DEFAULT
}

private class ScFonts(context: Context) {
    val light = FontFamily(typefaceWithCyrillicFallback(context, R.font.google_sans_flex_light, R.font.montserrat_light))
    val regular = FontFamily(typefaceWithCyrillicFallback(context, R.font.google_sans_flex_regular, R.font.montserrat_regular))
    val medium = FontFamily(typefaceWithCyrillicFallback(context, R.font.google_sans_flex_medium, R.font.montserrat_medium))
    val semiBold = FontFamily(typefaceWithCyrillicFallback(context, R.font.google_sans_flex_semibold, R.font.montserrat_semibold))
    val bold = FontFamily(typefaceWithCyrillicFallback(context, R.font.google_sans_flex_bold, R.font.montserrat_bold))
}

@Composable
fun rememberSCTypography(): Typography {
    val context = LocalContext.current
    val fonts = remember { ScFonts(context) }
    return remember(fonts) {
        Typography(
            displayLarge = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 64.sp),
            displayMedium = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 52.sp),
            displaySmall = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
            headlineLarge = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
            headlineSmall = TextStyle(fontFamily = fonts.bold, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontFamily = fonts.semiBold, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = fonts.semiBold, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            titleSmall = TextStyle(fontFamily = fonts.semiBold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontFamily = fonts.medium, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = fonts.medium, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = fonts.regular, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = TextStyle(fontFamily = fonts.semiBold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontFamily = fonts.medium, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
            labelSmall = TextStyle(fontFamily = fonts.medium, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
        )
    }
}
