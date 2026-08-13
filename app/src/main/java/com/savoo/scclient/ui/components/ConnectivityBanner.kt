package com.savoo.scclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.data.remote.ConnectivityEventBus
import kotlinx.coroutines.delay

private enum class ConnectivityBannerMode { OFFLINE, RESTORED }

@Composable
fun ConnectivityBanner(modifier: Modifier = Modifier) {
    val unreachableTick by ConnectivityEventBus.unreachableTick.collectAsState()
    val restoredTick by ConnectivityEventBus.restoredTick.collectAsState()
    var mode by remember { mutableStateOf(ConnectivityBannerMode.OFFLINE) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(unreachableTick) {
        if (unreachableTick == 0) return@LaunchedEffect
        mode = ConnectivityBannerMode.OFFLINE
        visible = true
        delay(2000)
        visible = false
    }

    LaunchedEffect(restoredTick) {
        if (restoredTick == 0) return@LaunchedEffect
        mode = ConnectivityBannerMode.RESTORED
        visible = true
        delay(2000)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val isOffline = mode == ConnectivityBannerMode.OFFLINE
        val containerColor = if (isOffline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        val contentColor = if (isOffline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = containerColor,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    if (isOffline) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (isOffline) R.string.network_unavailable else R.string.network_restored),
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
