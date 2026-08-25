package com.savoo.scclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.ui.haptics.rememberHaptics

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackSelectionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectAll: (() -> Unit)? = null,
    onFavoriteAll: (() -> Unit)? = null,
    onDownloadAll: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()

    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
            slideInVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                initialOffsetY = { it },
            ),
        exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)) +
            slideOutVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                targetOffsetY = { it },
            ),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { haptics.click(); onClear() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.selection_clear))
                }

                var pulse by remember { mutableStateOf(false) }
                LaunchedEffect(selectedCount) { pulse = true }
                val countScale by animateFloatAsState(
                    targetValue = if (pulse) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    label = "selectionCountScale",
                    finishedListener = { pulse = false },
                )
                Text(
                    text = stringResource(R.string.selection_count, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { scaleX = countScale; scaleY = countScale },
                )

                ButtonGroup(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    if (onSelectAll != null) {
                        SelectionAction(
                            icon = Icons.Filled.SelectAll,
                            label = stringResource(R.string.selection_select_all),
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            onClick = { haptics.click(); onSelectAll() },
                        )
                    }
                    if (onFavoriteAll != null) {
                        SelectionAction(
                            icon = Icons.Filled.Favorite,
                            label = stringResource(R.string.selection_favorite_all),
                            shapes = if (onSelectAll == null) ButtonGroupDefaults.connectedLeadingButtonShapes()
                                else ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            onClick = { haptics.like(); onFavoriteAll() },
                        )
                    }
                    if (onDownloadAll != null) {
                        SelectionAction(
                            icon = Icons.Filled.CloudDownload,
                            label = stringResource(R.string.selection_download_all),
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            onClick = { haptics.click(); onDownloadAll() },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    shapes: androidx.compose.material3.ToggleButtonShapes,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = false,
        onCheckedChange = { onClick() },
        shapes = shapes,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
    }
}
