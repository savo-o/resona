package com.savoo.scclient.ui.screens.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.player.QueueEntry
import com.savoo.scclient.ui.haptics.rememberHapticTick
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    controller: PlayerController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        QueueContent(
            controller = controller,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
        )
    }
}

@Composable
fun QueueContent(
    controller: PlayerController,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticTick()
    val hapticFeedback = LocalHapticFeedback.current
    val entries by controller.queueEntries.collectAsState()
    val state by controller.state.collectAsState()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        controller.moveQueueItem(from.index, to.index)
    }

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Text(
            stringResource(R.string.player_queue),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        state.currentTrack?.let { current ->
            Text(
                playingFromSource(state.queueTag, current.user.username),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(state = lazyListState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                ReorderableItem(reorderableState, key = entry.id) { isDragging ->
                    QueueRow(
                        entry = entry,
                        isCurrent = index == state.queueIndex,
                        isDragging = isDragging,
                        onPlay = { haptic(); controller.playFromQueue(index) },
                        onRemove = { haptic(); controller.removeQueueItem(index) },
                        dragModifier = Modifier.longPressDraggableHandle(
                            onDragStarted = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragStopped = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) },
                        ),
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    isDragging: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier,
) {
    val track = entry.track
    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "queueRowElevation")

    Surface(
        modifier = dragModifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        shadowElevation = elevation,
        tonalElevation = elevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(vertical = 6.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            AsyncImage(
                model = track.artworkUrl?.replace("-large", "-t200x200"),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.user.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.player_queue_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
