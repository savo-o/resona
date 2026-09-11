package com.savoo.scclient.ui.components

import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.savoo.scclient.data.repository.DividerStyle

val LocalDividerStyle = compositionLocalOf { DividerStyle.SUBTLE }

@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    alpha: Float = 0.4f,
    thickness: Dp = DividerDefaults.Thickness,
) {
    val strength = when (LocalDividerStyle.current) {
        DividerStyle.HIDDEN -> 0f
        DividerStyle.SUBTLE -> 1f
        DividerStyle.BRIGHT -> 2f
    }
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = (alpha * strength).coerceAtMost(1f)),
    )
}
