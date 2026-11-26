package com.savoo.scclient.ui.screens.player

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class AliceOutputAction(
    val sent: Boolean,
    val ready: Boolean,
    val busy: Boolean,
    val failed: Boolean,
    val configured: Boolean,
    val onClick: () -> Unit,
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberAliceOutput(viewModel: PlayerViewModel, trackId: Long?): AliceOutputAction? {
    val ble = viewModel.aliceBle
    val enabled by ble.enabled.collectAsState()
    if (!enabled) return null

    val controller = viewModel.controller
    val key by ble.key.collectAsState()
    val scope = rememberCoroutineScope()
    var nearby by remember { mutableStateOf<BluetoothDevice?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val canScan = trackId != null

    LifecycleResumeEffect(key, canScan) {
        val scan = key?.takeIf { canScan }?.let { stationKey ->
            scope.launch { ble.nearby(stationKey).collect { nearby = it } }
        }
        onPauseOrDispose {
            scan?.cancel()
            nearby = null
        }
    }
    LaunchedEffect(sent) {
        if (sent) {
            delay(1_500)
            sent = false
        }
    }
    LaunchedEffect(failed) {
        if (failed) {
            delay(4_000)
            failed = false
        }
    }

    return AliceOutputAction(
        sent = sent,
        ready = trackId != null && key != null && nearby != null,
        busy = busy,
        failed = failed,
        configured = key != null,
        onClick = {
            val device = nearby
            val stationKey = key
            if (!busy && device != null && stationKey != null && trackId != null) {
                busy = true
                failed = false
                sent = false
                scope.launch {
                    try {
                        val accepted = ble.send(device, stationKey, trackId)
                        if (accepted) {
                            val local = controller.state.value
                            if (local.isPlaying && local.currentTrack?.id == trackId) {
                                controller.togglePlayPause()
                            }
                        }
                        sent = accepted
                        failed = !accepted
                    } finally {
                        busy = false
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AliceOutputButton(
    action: AliceOutputAction,
    tint: Color,
    accent: Color,
    background: Color = Color.Transparent,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        when {
            action.busy -> R.string.alice_ble_sending
            action.sent -> R.string.alice_ble_sent
            action.failed -> R.string.alice_ble_send_failed
            !action.configured -> R.string.alice_ble_setup_required
            !action.ready -> R.string.alice_ble_out_of_range
            else -> R.string.alice_ble_send
        },
    )
    val color = when {
        action.failed -> MaterialTheme.colorScheme.error
        action.sent -> accent
        else -> tint
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = action.onClick,
            modifier = modifier,
            enabled = action.ready && !action.busy,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = background,
                contentColor = color,
                disabledContainerColor = background,
                disabledContentColor = color.copy(alpha = if (action.busy) 1f else 0.38f),
            ),
        ) {
            if (action.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = color,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    if (action.sent) Icons.Filled.CastConnected else Icons.Filled.Cast,
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
