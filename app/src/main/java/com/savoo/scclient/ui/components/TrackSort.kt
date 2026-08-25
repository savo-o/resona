package com.savoo.scclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.ui.haptics.rememberHaptics

enum class TrackSortOption {
    DEFAULT, TITLE, ARTIST, DURATION;

    val labelRes: Int
        get() = when (this) {
            DEFAULT -> R.string.sort_default
            TITLE -> R.string.sort_title
            ARTIST -> R.string.sort_artist
            DURATION -> R.string.sort_duration
        }

    val icon: ImageVector
        get() = when (this) {
            DEFAULT -> Icons.AutoMirrored.Filled.PlaylistPlay
            TITLE -> Icons.Filled.SortByAlpha
            ARTIST -> Icons.Filled.Person
            DURATION -> Icons.Filled.Schedule
        }

    val ascendingLabelRes: Int
        get() = when (this) {
            DEFAULT -> R.string.sort_dir_original
            TITLE, ARTIST -> R.string.sort_dir_a_z
            DURATION -> R.string.sort_dir_shortest
        }

    val descendingLabelRes: Int
        get() = when (this) {
            DEFAULT -> R.string.sort_dir_reversed
            TITLE, ARTIST -> R.string.sort_dir_z_a
            DURATION -> R.string.sort_dir_longest
        }
}

data class TrackSort(
    val option: TrackSortOption = TrackSortOption.DEFAULT,
    val descending: Boolean = false,
) {
    val isActive: Boolean get() = option != TrackSortOption.DEFAULT || descending
}

fun List<Track>.applySortOption(sort: TrackSort): List<Track> {
    val ascending = when (sort.option) {
        TrackSortOption.DEFAULT -> this
        TrackSortOption.TITLE -> sortedBy { it.title.lowercase() }
        TrackSortOption.ARTIST -> sortedBy { it.user.username.lowercase() }
        TrackSortOption.DURATION -> sortedBy { it.durationMs }
    }
    return if (sort.descending) ascending.reversed() else ascending
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackSortButton(
    sort: TrackSort,
    onSortChange: (TrackSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val tint by animateColorAsState(
        targetValue = if (sort.isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sortButtonTint",
    )

    IconButton(
        onClick = { haptics.click(); showSheet = true },
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.sort_button), tint = tint)
    }

    if (showSheet) {
        TrackSortSheet(
            sort = sort,
            onSortChange = onSortChange,
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrackSortSheet(
    sort: TrackSort,
    onSortChange: (TrackSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val haptics = rememberHaptics()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.sort_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            TrackSortOption.entries.forEach { option ->
                SortOptionRow(
                    option = option,
                    selected = option == sort.option,
                    onClick = {
                        haptics.click()
                        onSortChange(sort.copy(option = option))
                    },
                )
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.sort_direction),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            ButtonGroup(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DirectionButton(
                    label = stringResource(sort.option.ascendingLabelRes),
                    icon = Icons.Filled.ArrowUpward,
                    checked = !sort.descending,
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    onClick = {
                        haptics.click()
                        onSortChange(sort.copy(descending = false))
                    },
                    modifier = Modifier.weight(1f),
                )
                DirectionButton(
                    label = stringResource(sort.option.descendingLabelRes),
                    icon = Icons.Filled.ArrowDownward,
                    checked = sort.descending,
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    onClick = {
                        haptics.click()
                        onSortChange(sort.copy(descending = true))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SortOptionRow(
    option: TrackSortOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val corner by animateDpAsState(
        targetValue = if (isPressed) 12.dp else if (selected) 28.dp else 18.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "sortRowCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "sortRowScale",
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sortRowContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sortRowContent",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(option.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(option.labelRes),
            style = MaterialTheme.typography.titleMedium,
            color = content,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)) +
                scaleOut(spring(stiffness = Spring.StiffnessMedium)),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DirectionButton(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    shapes: androidx.compose.material3.ToggleButtonShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { if (it) onClick() },
        shapes = shapes,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
