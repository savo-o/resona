package com.savoo.scclient.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.data.repository.FavoritesSyncState
import com.savoo.scclient.data.repository.FavoritesSyncStop
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.savoo.scclient.data.repository.PlaylistAddProgress

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TaskProgressBanner(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    fraction: Float?,
    finished: Boolean,
    failed: Boolean,
    onCancel: () -> Unit,
    cancelDescription: String,
    modifier: Modifier = Modifier,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "taskProgress",
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        failed -> Icons.Filled.ErrorOutline
                        finished -> Icons.Filled.CheckCircle
                        else -> icon
                    },
                    contentDescription = null,
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (finished) {
                    Spacer(Modifier.height(48.dp))
                } else {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = cancelDescription)
                    }
                }
            }
            if (!finished) {
                Spacer(Modifier.height(6.dp))
                if (fraction == null) {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 10.dp))
                } else {
                    LinearWavyProgressIndicator(
                        progress = { animatedFraction },
                        modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistAddBanner(progress: PlaylistAddProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    TaskProgressBanner(
        icon = Icons.Filled.LibraryAdd,
        title = when {
            progress.isFull -> stringResource(R.string.playlist_error_full)
            progress.errorMessage != null -> stringResource(R.string.playlist_error_generic, progress.errorMessage)
            progress.finished -> stringResource(R.string.playlist_bulk_done, progress.added, progress.title)
            progress.fraction == null -> stringResource(R.string.playlist_bulk_progress_online, progress.total, progress.title)
            else -> stringResource(R.string.playlist_bulk_progress, progress.done, progress.total, progress.title)
        },
        subtitle = if (progress.finished && !progress.failed && progress.added < progress.total) {
            stringResource(R.string.playlist_bulk_skipped, progress.total - progress.added)
        } else {
            null
        },
        fraction = progress.fraction,
        finished = progress.finished,
        failed = progress.failed,
        onCancel = onCancel,
        cancelDescription = stringResource(R.string.cancel),
        modifier = modifier,
    )
}

@Composable
fun FavoritesSyncBanner(state: FavoritesSyncState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    TaskProgressBanner(
        icon = Icons.Filled.CloudUpload,
        title = when {
            state.aborted -> stringResource(R.string.favorites_push_aborted, state.synced, state.total)
            state.finished -> stringResource(R.string.favorites_push_done, state.synced, state.total)
            else -> stringResource(R.string.favorites_push_progress, (state.done + 1).coerceAtMost(state.total), state.total)
        },
        subtitle = when {
            state.stop == FavoritesSyncStop.SPAM_WARNING -> state.blockedUntil?.let {
                stringResource(
                    R.string.favorites_push_spam_until,
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                        .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())),
                )
            } ?: stringResource(R.string.favorites_push_spam)
            state.stop == FavoritesSyncStop.CAPTCHA -> stringResource(R.string.favorites_push_captcha)
            state.aborted -> stringResource(R.string.favorites_push_aborted_desc)
            state.failed > 0 -> stringResource(R.string.favorites_push_failed, state.failed)
            state.unavailable > 0 && (state.finished || state.currentTitle == null) ->
                stringResource(R.string.favorites_push_unavailable, state.unavailable)
            else -> state.currentTitle
        },
        fraction = state.fraction,
        finished = state.finished,
        failed = state.aborted || (state.finished && state.failed > 0),
        onCancel = onCancel,
        cancelDescription = stringResource(R.string.cancel),
        modifier = modifier,
    )
}
