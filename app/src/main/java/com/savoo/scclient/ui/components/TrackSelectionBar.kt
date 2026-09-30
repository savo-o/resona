package com.savoo.scclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
    onQueueAll: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()

    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
            slideInVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                initialOffsetY = { it },
            ) +
            scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
            ),
        exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)) +
            slideOutVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                targetOffsetY = { it },
            ) +
            scaleOut(
                targetScale = 0.9f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            ),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                        .padding(end = 8.dp)
                        .graphicsLayer { scaleX = countScale; scaleY = countScale },
                )

                val actions = buildList {
                    if (onSelectAll != null) {
                        add(
                            SelectionActionSpec(
                                icon = Icons.Filled.SelectAll,
                                label = stringResource(R.string.selection_select_all),
                                onClick = { haptics.click(); onSelectAll() },
                            )
                        )
                    }
                    if (onQueueAll != null) {
                        add(
                            SelectionActionSpec(
                                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                                label = stringResource(R.string.selection_queue_all),
                                onClick = { haptics.click(); onQueueAll() },
                            )
                        )
                    }
                    if (onFavoriteAll != null) {
                        add(
                            SelectionActionSpec(
                                icon = Icons.Filled.Favorite,
                                label = stringResource(R.string.selection_favorite_all),
                                onClick = { haptics.like(); onFavoriteAll() },
                            )
                        )
                    }
                    if (onDownloadAll != null) {
                        add(
                            SelectionActionSpec(
                                icon = Icons.Filled.CloudDownload,
                                label = stringResource(R.string.selection_download_all),
                                onClick = { haptics.click(); onDownloadAll() },
                            )
                        )
                    }
                    if (onAddToPlaylist != null) {
                        add(
                            SelectionActionSpec(
                                icon = Icons.Filled.LibraryAdd,
                                label = stringResource(R.string.selection_add_to_playlist),
                                onClick = { haptics.click(); onAddToPlaylist() },
                            )
                        )
                    }
                    if (onRemoveFromPlaylist != null) {
                        add(
                            SelectionActionSpec(
                                icon = Icons.Filled.PlaylistRemove,
                                label = stringResource(R.string.selection_remove_from_playlist),
                                onClick = { haptics.click(); onRemoveFromPlaylist() },
                            )
                        )
                    }
                }
                val visibleActions = if (actions.size > MAX_VISIBLE_SELECTION_ACTIONS) actions.take(MAX_VISIBLE_SELECTION_ACTIONS - 1) else actions
                val overflowActions = actions.drop(visibleActions.size)
                var overflowOpen by remember { mutableStateOf(false) }

                ButtonGroup(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    val lastIndex = if (overflowActions.isEmpty()) visibleActions.lastIndex else visibleActions.size
                    visibleActions.forEachIndexed { index, action ->
                        SelectionAction(
                            icon = action.icon,
                            label = action.label,
                            shapes = when {
                                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                index == lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            onClick = action.onClick,
                        )
                    }
                    if (overflowActions.isNotEmpty()) {
                        Box {
                            SelectionAction(
                                icon = Icons.Filled.MoreVert,
                                label = stringResource(R.string.selection_more),
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                onClick = { haptics.click(); overflowOpen = true },
                            )
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                overflowActions.forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.label) },
                                        leadingIcon = { Icon(action.icon, contentDescription = null) },
                                        onClick = { overflowOpen = false; action.onClick() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_VISIBLE_SELECTION_ACTIONS = 4

private data class SelectionActionSpec(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    shapes: ToggleButtonShapes,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = false,
        onCheckedChange = { onClick() },
        shapes = shapes,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
    }
}
