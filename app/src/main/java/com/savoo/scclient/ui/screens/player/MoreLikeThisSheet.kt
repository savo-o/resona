package com.savoo.scclient.ui.screens.player

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.player.PlayerController
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreLikeThisSheet(controller: PlayerController, track: Track, onDismiss: () -> Unit) {
    var count by remember { mutableFloatStateOf(8f) }
    var infinite by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { if (!loading) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.player_more_like_this), style = MaterialTheme.typography.headlineSmall)
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.similar_count, count.roundToInt()), style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = count,
                        onValueChange = { count = it },
                        valueRange = 1f..50f,
                        steps = 48,
                        enabled = !infinite && !loading,
                    )
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = infinite,
                            enabled = !loading,
                            role = Role.Switch,
                            onValueChange = { infinite = it },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Infinite", style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.similar_infinite_description), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = infinite, onCheckedChange = null, enabled = !loading)
                    }
                }
            }
            if (failed) Text(stringResource(R.string.player_more_like_this_empty), color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    loading = true
                    failed = false
                    scope.launch {
                        try {
                            if (controller.startSimilarQueue(track, count.roundToInt(), infinite) > 0) onDismiss()
                            else failed = true
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.similar_start))
            }
        }
    }
}
