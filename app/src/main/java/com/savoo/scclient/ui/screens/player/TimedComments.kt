package com.savoo.scclient.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.model.TrackComment
import com.savoo.scclient.data.repository.CommentPostException
import com.savoo.scclient.data.repository.TimedCommentsRate
import com.savoo.scclient.ui.components.PlayerInfoBanner
import com.savoo.scclient.ui.components.hiResArtwork
import kotlinx.coroutines.launch

private const val COMMENT_EXACT_WINDOW_MS = 5_000L
private const val COMMENT_NEAR_MS = 6_000L
private val COMMENT_ROW_HEIGHT = 34.dp
private const val MARKER_BUCKETS = 40

@Composable
fun rememberVisibleComment(
    comments: List<TrackComment>,
    positionMs: Long,
    durationMs: Long,
    rate: TimedCommentsRate,
    pinned: TrackComment? = null,
): TrackComment? {
    if (pinned != null) return pinned
    if (rate == TimedCommentsRate.EXACT) {
        val timed = remember(comments) {
            comments.filter { (it.timestampMs ?: -1L) >= 0 }.sortedBy { it.timestampMs }
        }
        val comment = timed.lastOrNull { (it.timestampMs ?: 0L) <= positionMs } ?: return null
        val at = comment.timestampMs ?: return null
        return comment.takeIf { positionMs - at < COMMENT_EXACT_WINDOW_MS }
    }
    val slotMs = rate.slotMs()
    val schedule = remember(comments, durationMs, slotMs) { buildCommentSchedule(comments, durationMs, slotMs) }
    if (schedule.isEmpty()) return null
    val slot = (positionMs / slotMs).toInt()
    return schedule.getOrNull(slot) ?: schedule.lastOrNull()
}

private fun TimedCommentsRate.slotMs(): Long = when (this) {
    TimedCommentsRate.OFTEN -> 1_500L
    TimedCommentsRate.NORMAL -> 3_000L
    TimedCommentsRate.RARE -> 6_000L
    TimedCommentsRate.EXACT -> 1_500L
}

private fun buildCommentSchedule(comments: List<TrackComment>, durationMs: Long, slotMs: Long): List<TrackComment?> {
    val timed = comments.filter { (it.timestampMs ?: -1L) >= 0 }.sortedBy { it.timestampMs }
    if (timed.isEmpty() || durationMs <= 0L) return emptyList()
    val slots = (durationMs / slotMs).toInt() + 1
    var previous: TrackComment? = null
    return List(slots) { slot ->
        val start = slot * slotMs
        val near = timed.filter { (it.timestampMs ?: 0L) in (start - COMMENT_NEAR_MS)..(start + COMMENT_NEAR_MS) }
        val pool = near.filter { it.id != previous?.id }.ifEmpty { near }
        val pick = pool.randomOrNull()
            ?: timed.filter { it.id != previous?.id }.ifEmpty { timed }
                .minByOrNull { kotlin.math.abs((it.timestampMs ?: 0L) - start) }
        previous = pick
        pick
    }
}

fun Modifier.timedCommentMarkers(
    comments: List<TrackComment>,
    durationMs: Long,
    activeId: Long?,
    color: Color,
    activeColor: Color,
    trackInset: Dp,
    lift: Dp = 9.dp,
): Modifier = composed {
    if (comments.isEmpty() || durationMs <= 0L) return@composed this
    val markers = remember(comments, durationMs) {
        comments
            .filter { (it.timestampMs ?: -1L) in 0..durationMs }
            .groupBy { ((it.timestampMs ?: 0L) * MARKER_BUCKETS / durationMs).toInt() }
            .mapNotNull { (_, bucket) -> bucket.firstOrNull() }
    }
    val density = LocalDensity.current
    val insetPx = with(density) { trackInset.toPx() }
    val radiusPx = with(density) { 2.dp.toPx() }
    val liftPx = with(density) { lift.toPx() }
    drawWithContent {
        drawContent()
        val usable = (size.width - insetPx * 2).coerceAtLeast(1f)
        val y = size.height / 2f - liftPx
        markers.forEach { comment ->
            val at = comment.timestampMs ?: return@forEach
            val fraction = (at.toFloat() / durationMs).coerceIn(0f, 1f)
            val isActive = comment.id == activeId
            drawCircle(
                color = if (isActive) activeColor else color,
                radius = if (isActive) radiusPx * 1.6f else radiusPx,
                center = Offset(insetPx + usable * fraction, y),
            )
        }
    }
}

@Composable
fun TimedCommentChip(
    comment: TrackComment?,
    enabled: Boolean,
    accent: Color,
    onColor: Color,
    surfaceColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    if (!enabled) return
    AnimatedContent(
        targetState = comment,
        transitionSpec = {
            (fadeIn(tween(220)) togetherWith fadeOut(tween(180)))
                .using(SizeTransform(clip = false) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) })
        },
        contentKey = { it?.id },
        label = "timedCommentChip",
        modifier = modifier.fillMaxWidth(),
    ) { shown ->
        if (shown == null) {
            Spacer(Modifier.fillMaxWidth().height(0.dp))
            return@AnimatedContent
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(COMMENT_ROW_HEIGHT)
                .clip(RoundedCornerShape(50))
                .background(if (highlighted) accent.copy(alpha = 0.28f) else surfaceColor.copy(alpha = 0.8f))
                .clickable { onClick() }
                .padding(start = 6.dp, end = 12.dp),
        ) {
            CommentAvatar(shown, accent, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                shown.user.username,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 90.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                shown.body.trim().replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = onColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimedCommentsSheet(
    comments: List<TrackComment>,
    positionMs: Long,
    accent: Color,
    canComment: Boolean,
    currentUserId: Long?,
    initialHighlightId: Long?,
    onSubmit: suspend (String, Long) -> Result<TrackComment>,
    onSelect: (TrackComment) -> Unit,
    onPosted: (TrackComment) -> Unit,
    onDelete: (TrackComment) -> Unit,
    onDismiss: () -> Unit,
    onUserClick: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var submissionError by remember { mutableStateOf<Int?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var highlightedId by remember { mutableStateOf(initialHighlightId) }
    val draftPositionMs = remember { positionMs }
    val listState = rememberLazyListState()
    val startIndex = remember(comments.size) {
        val byHighlight = initialHighlightId?.let { id -> comments.indexOfFirst { it.id == id } } ?: -1
        if (byHighlight >= 0) byHighlight
        else comments.indexOfLast { (it.timestampMs ?: 0L) <= positionMs }.coerceAtLeast(0)
    }
    LaunchedEffect(Unit) { listState.scrollToItem(startIndex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.92f)) {
                Text(
                    pluralStringResource(R.plurals.player_comments_count, comments.size, comments.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                )
                PlayerInfoBanner(
                    message = banner,
                    onHide = { banner = null },
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                )
                if (comments.isEmpty()) {
                    Text(
                        stringResource(R.string.player_comments_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(comments, key = { it.id }) { comment ->
                        val isHighlighted = highlightedId == comment.id
                        val rowColor by animateColorAsState(
                            targetValue = if (isHighlighted) accent.copy(alpha = 0.2f) else Color.Transparent,
                            animationSpec = tween(if (isHighlighted) 120 else 900),
                            label = "commentHighlight",
                        )
                        LaunchedEffect(isHighlighted) {
                            if (isHighlighted) {
                                kotlinx.coroutines.delay(1_800)
                                if (highlightedId == comment.id) highlightedId = null
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowColor)
                                .clickable {
                                    highlightedId = comment.id
                                    onSelect(comment)
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                        ) {
                            val openProfile = Modifier.clickable(enabled = comment.user.id != 0L) {
                                onUserClick(comment.user.id)
                            }
                            CommentAvatar(comment, accent, 34.dp, openProfile)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        comment.user.username,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false).then(openProfile),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        formatTime(comment.timestampMs ?: 0L),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                    )
                                    if (currentUserId != null && comment.user.id == currentUserId) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { onDelete(comment) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = stringResource(R.string.player_comment_delete),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    comment.body.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (canComment) {
                    submissionError?.let { message ->
                        Text(
                            text = stringResource(message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                submissionError = null
                            },
                            enabled = !sending,
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            placeholder = {
                                Text(stringResource(R.string.player_comment_hint, formatTime(draftPositionMs)))
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                sending = true
                                submissionError = null
                                scope.launch {
                                    try {
                                        onSubmit(draft, draftPositionMs)
                                            .onSuccess { posted ->
                                                draft = ""
                                                highlightedId = posted.id
                                                banner = context.getString(R.string.player_comment_sent)
                                                onPosted(posted)
                                            }
                                            .onFailure { failure ->
                                                val response = failure as? CommentPostException
                                                submissionError = when {
                                                    response?.verificationRequired == true -> R.string.player_comment_verification_required
                                                    response?.status == 401 -> R.string.player_comment_session_rejected
                                                    response?.status == 403 -> R.string.player_comment_forbidden
                                                    response?.status == 429 -> R.string.player_comment_rate_limited
                                                    response?.status == 0 -> R.string.player_comment_unconfirmed
                                                    else -> R.string.player_comment_failed
                                                }
                                            }
                                    } finally {
                                        sending = false
                                    }
                                }
                            },
                            enabled = !sending && draft.isNotBlank(),
                        ) {
                            if (sending) {
                                LoadingIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.player_comment_send))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(comment: TrackComment, accent: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f))
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        val avatar = comment.user.avatarUrl
        if (avatar != null) {
            AsyncImage(
                model = hiResArtwork(avatar),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}
