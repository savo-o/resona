package com.savoo.scclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.mutableIntStateOf
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
    val isUnreachable by ConnectivityEventBus.isUnreachable.collectAsState()
    val restoredTick by ConnectivityEventBus.restoredTick.collectAsState()
    var mode by remember { mutableStateOf(ConnectivityBannerMode.OFFLINE) }
    var visible by remember { mutableStateOf(false) }
    var compact by remember { mutableStateOf(false) }
    var expandTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(isUnreachable) {
        if (isUnreachable) {
            mode = ConnectivityBannerMode.OFFLINE
            compact = false
            visible = true
        }
    }

    LaunchedEffect(isUnreachable, mode, expandTick) {
        if (!isUnreachable || mode != ConnectivityBannerMode.OFFLINE) return@LaunchedEffect
        compact = false
        delay(3000)
        compact = true
    }

    LaunchedEffect(restoredTick) {
        if (restoredTick == 0) return@LaunchedEffect
        mode = ConnectivityBannerMode.RESTORED
        compact = false
        visible = true
        delay(2000)
        if (!ConnectivityEventBus.isUnreachable.value) visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
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
            onClick = { if (compact) expandTick++ },
        ) {
            val horizontalPadding by animateDpAsState(
                targetValue = if (compact) 10.dp else 16.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                label = "bannerPadding",
            )
            val verticalPadding by animateDpAsState(
                targetValue = if (compact) 6.dp else 10.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                label = "bannerPadding",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            ) {
                Icon(
                    if (isOffline) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                AnimatedVisibility(
                    visible = !compact,
                    enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
    }
}
