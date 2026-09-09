package com.savoo.scclient.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Uses the current window, so resizing, rotation and folding do not need device checks. */
@Composable
internal fun AdaptivePlayerLayout(
    modifier: Modifier = Modifier,
    showLyrics: Boolean = false,
    header: @Composable ColumnScope.(Boolean, Boolean) -> Unit,
    lyrics: @Composable () -> Unit,
    middle: @Composable (Boolean, Boolean) -> Unit,
    controls: @Composable (Boolean, Boolean) -> Unit,
) {
    BoxWithConstraints(modifier) {
        if (showLyrics) {
            lyrics()
        }
        val landscape = maxWidth >= maxHeight
        val twoColumns = landscape
        val compact = maxHeight / LocalDensity.current.fontScale.coerceAtLeast(1f) < 480.dp
        val mini = landscape && compact
        val gutter = if (compact) 16.dp else if (twoColumns) 32.dp else 24.dp
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = if (landscape) Dp.Infinity else 720.dp)
                    .fillMaxSize()
                    .padding(horizontal = gutter)
                    .padding(top = if (compact) 8.dp else 18.dp, bottom = if (compact) 8.dp else 12.dp)
                    .testTag(if (mini) "player_mini" else if (twoColumns) "player_wide" else "player_portrait"),
            ) {
                header(compact, mini)
                if (mini) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            controls(true, true)
                        }
                    }
                } else if (twoColumns) {
                    Row(
                        Modifier.weight(1f).fillMaxWidth().padding(top = if (compact) 8.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 32.dp),
                    ) {
                        Box(Modifier.weight(1f).fillMaxHeight()) { middle(compact, landscape) }
                        // Keep every action reachable with large fonts and short split-screen windows.
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Column(Modifier.fillMaxWidth()) {
                                controls(compact, false)
                            }
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) { middle(compact, landscape) }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = if (landscape) 1040.dp else 720.dp).fillMaxWidth()) {
                            controls(compact, false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AdaptivePlayerControls(
    horizontal: Boolean,
    seekSpacing: Dp,
    actionSpacing: Dp,
    seek: @Composable () -> Unit,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    if (horizontal) {
        Column {
            seek()
            Spacer(Modifier.height(seekSpacing))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { primary() }
                Box(Modifier.weight(1f)) { secondary() }
            }
        }
    } else {
        Column {
            seek()
            Spacer(Modifier.height(seekSpacing))
            primary()
            Spacer(Modifier.height(actionSpacing))
            secondary()
        }
    }
}

@Composable
internal fun AdaptivePlayerSeek(
    horizontal: Boolean,
    elapsed: String,
    duration: String,
    color: Color,
    slider: @Composable () -> Unit,
) {
    if (horizontal) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(elapsed, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
            Box(Modifier.weight(1f)) { slider() }
            Text(duration, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
        }
    } else {
        Column {
            slider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(elapsed, style = MaterialTheme.typography.bodySmall, color = color)
                Text(duration, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}
