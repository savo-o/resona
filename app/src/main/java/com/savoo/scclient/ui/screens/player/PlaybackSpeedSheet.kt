package com.savoo.scclient.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.haptics.rememberHapticTick
import kotlin.math.roundToInt

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedSheet(
    controller: PlayerController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        PlaybackSpeedContent(controller = controller)
    }
}

@UnstableApi
@Composable
fun PlaybackSpeedContent(
    controller: PlayerController,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticTick()
    val state by controller.state.collectAsState()
    val speed = state.playbackSpeed

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.player_speed),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.player_speed_format, formatSpeed(speed)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))

        Slider(
            value = speed,
            onValueChange = { controller.setPlaybackSpeed(snapSpeed(it)) },
            onValueChangeFinished = { haptic() },
            valueRange = PlayerController.MIN_PLAYBACK_SPEED..PlayerController.MAX_PLAYBACK_SPEED,
            steps = SLIDER_STEPS,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(PlayerController.PLAYBACK_SPEED_STEPS) { option ->
                FilterChip(
                    selected = isSameSpeed(option, speed),
                    onClick = { haptic(); controller.setPlaybackSpeed(option) },
                    label = { Text(stringResource(R.string.player_speed_format, formatSpeed(option))) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (!isSameSpeed(speed, 1f)) {
            OutlinedButton(
                onClick = { haptic(); controller.setPlaybackSpeed(1f) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.player_speed_reset))
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

private const val SPEED_STEP = 0.05f
private val SLIDER_STEPS =
    ((PlayerController.MAX_PLAYBACK_SPEED - PlayerController.MIN_PLAYBACK_SPEED) / SPEED_STEP).roundToInt() - 1

private fun snapSpeed(value: Float): Float =
    ((value / SPEED_STEP).roundToInt() * SPEED_STEP)
        .coerceIn(PlayerController.MIN_PLAYBACK_SPEED, PlayerController.MAX_PLAYBACK_SPEED)

private fun isSameSpeed(a: Float, b: Float): Boolean = kotlin.math.abs(a - b) < 0.001f

fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).roundToInt()
    return when {
        rounded % 100 == 0 -> (rounded / 100).toString()
        rounded % 10 == 0 -> "%.1f".format(speed)
        else -> "%.2f".format(speed)
    }
}
