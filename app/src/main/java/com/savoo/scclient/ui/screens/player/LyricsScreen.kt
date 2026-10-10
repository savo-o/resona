package com.savoo.scclient.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import com.savoo.scclient.data.model.GeniusAnnotation
import kotlin.math.roundToInt
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import com.savoo.scclient.R
import com.savoo.scclient.data.model.LyricsLine
import com.savoo.scclient.data.model.LyricsResult
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.ui.components.MorphingArtworkShape
import com.savoo.scclient.ui.haptics.rememberHapticTick
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val LYRICS_LEAD_MS = 150L
private const val INTRO_MIN_MS = 2_500L
private const val INTERLUDE_MIN_MS = 4_000L
private const val FOCUS_FRACTION = 0.36f

@Composable
internal fun PlayerLyricsScreen(
    onClose: () -> Unit,
    backgroundColor: Color,
    result: LyricsResult?,
    activeIndex: Int,
    onSeek: (Long) -> Unit,
    sync: LyricsSyncState,
    onSyncAction: (LyricsSyncAction) -> Unit,
    accent: Color,
    onColor: Color,
    mutedColor: Color,
    surfaceColor: Color,
    track: Track?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    annotations: Map<Int, List<GeniusAnnotation>> = emptyMap(),
    onRequestAnnotations: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(backgroundColor)) {
            Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
                LyricsHeader(track = track, onColor = onColor, mutedColor = mutedColor, surfaceColor = surfaceColor, onClose = onClose)
                LyricsView(
                    result = result,
                    activeIndex = activeIndex,
                    onSeek = onSeek,
                    sync = sync,
                    onSyncAction = onSyncAction,
                    accent = accent,
                    onColor = onColor,
                    mutedColor = mutedColor,
                    surfaceColor = surfaceColor,
                    track = track,
                    positionMs = positionMs,
                    isPlaying = isPlaying,
                    large = true,
                    annotations = annotations,
                    onRequestAnnotations = onRequestAnnotations,
                    modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(sheetDragGuard),
                )
                LyricsTransport(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    accent = accent,
                    onColor = onColor,
                    mutedColor = mutedColor,
                    surfaceColor = surfaceColor,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrev = onPrev,
                )
            }
        }
    }
}

@Composable
private fun LyricsHeader(track: Track?, onColor: Color, mutedColor: Color, surfaceColor: Color, onClose: () -> Unit) {
    val haptic = rememberHapticTick()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIconButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.player_close_lyrics),
            onClick = { haptic(); onClose() },
            tint = onColor,
            background = surfaceColor,
            iconSize = 24.dp,
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                track?.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = onColor,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                track?.user?.let { it.fullName?.takeIf { name -> name.isNotBlank() } ?: it.username }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun LyricsTransport(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    accent: Color,
    onColor: Color,
    mutedColor: Color,
    surfaceColor: Color,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    val haptic = rememberHapticTick()
    val progress by animateFloatAsState(
        targetValue = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(500, easing = LinearEasing),
        label = "lyricsProgress",
    )
    val accentInk = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val prevSource = remember { MutableInteractionSource() }
    val playSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }
    val prevPressed by prevSource.collectIsPressedAsState()
    val playPressed by playSource.collectIsPressedAsState()
    val nextPressed by nextSource.collectIsPressedAsState()
    val anyPressed = prevPressed || playPressed || nextPressed
    val widthSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
    fun pillWeight(pressed: Boolean) = when {
        pressed -> 1.45f
        anyPressed -> 0.8f
        else -> 1f
    }
    val prevWeight by animateFloatAsState(pillWeight(prevPressed), widthSpring, label = "lyricsPrevWeight")
    val playWeight by animateFloatAsState(pillWeight(playPressed), widthSpring, label = "lyricsPlayWeight")
    val nextWeight by animateFloatAsState(pillWeight(nextPressed), widthSpring, label = "lyricsNextWeight")

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(mutedColor.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelPillButton(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_previous),
                onClick = { haptic(); onPrev() },
                background = surfaceColor,
                tint = onColor,
                interactionSource = prevSource,
                height = 56.dp,
                modifier = Modifier.weight(prevWeight),
            )
            PixelPillButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                onClick = { haptic(); onTogglePlay() },
                background = accent,
                tint = accentInk,
                iconSize = 30.dp,
                interactionSource = playSource,
                height = 56.dp,
                modifier = Modifier.weight(playWeight),
            )
            PixelPillButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_next),
                onClick = { haptic(); onNext() },
                background = surfaceColor,
                tint = onColor,
                interactionSource = nextSource,
                height = 56.dp,
                modifier = Modifier.weight(nextWeight),
            )
        }
    }
}

@Composable
private fun rememberLyricsClock(positionMs: Long, isPlaying: Boolean, sync: LyricsSyncState): State<Long> {
    val latestSync by rememberUpdatedState(sync.sync)
    return produceState(initialValue = latestSync.lyricsTimeAt(positionMs) + LYRICS_LEAD_MS, positionMs, isPlaying) {
        val base = positionMs
        val startNanos = System.nanoTime()
        value = latestSync.lyricsTimeAt(base) + LYRICS_LEAD_MS
        if (!isPlaying) return@produceState
        while (true) {
            withFrameNanos { now ->
                val elapsed = ((now - startNanos) / 1_000_000L).coerceIn(0L, 1_500L)
                value = latestSync.lyricsTimeAt(base + elapsed) + LYRICS_LEAD_MS
            }
        }
    }
}

private sealed interface LyricsRow {
    val startMs: Long
    val endMs: Long
    val lineIndex: Int

    data class Words(val text: String, override val startMs: Long, override val endMs: Long, override val lineIndex: Int) : LyricsRow
    data class Interlude(override val startMs: Long, override val endMs: Long, override val lineIndex: Int) : LyricsRow
}

private fun buildRows(lines: List<LyricsLine>): List<LyricsRow> {
    val rows = mutableListOf<LyricsRow>()
    val first = lines.firstOrNull() ?: return rows
    if (first.timeMs >= INTRO_MIN_MS) rows += LyricsRow.Interlude(0L, first.timeMs, -1)
    lines.forEachIndexed { index, line ->
        val next = lines.drop(index + 1).firstOrNull { it.text.isNotBlank() }?.timeMs
        val nextAny = lines.getOrNull(index + 1)?.timeMs
        if (line.text.isBlank()) {
            val end = next ?: (line.timeMs + INTERLUDE_MIN_MS)
            rows += LyricsRow.Interlude(line.timeMs, end, index)
        } else {
            rows += LyricsRow.Words(line.text.trim(), line.timeMs, nextAny ?: (line.timeMs + 5_000L), index)
        }
    }
    return rows
}

private fun fillDurationMs(row: LyricsRow.Words): Long {
    val gap = (row.endMs - row.startMs).coerceAtLeast(300L)
    val estimate = 400L + row.text.length * 65L
    return minOf((gap * 0.95f).toLong(), estimate).coerceAtLeast(300L)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun LyricsView(
    result: LyricsResult?,
    activeIndex: Int,
    onSeek: (Long) -> Unit,
    sync: LyricsSyncState,
    onSyncAction: (LyricsSyncAction) -> Unit,
    accent: Color,
    onColor: Color,
    mutedColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    offsetControlTopPadding: Dp = 4.dp,
    track: Track? = null,
    positionMs: Long = 0L,
    isPlaying: Boolean = false,
    large: Boolean = false,
    annotations: Map<Int, List<GeniusAnnotation>> = emptyMap(),
    onRequestAnnotations: () -> Unit = {},
) {
    var shareFrom by remember { mutableStateOf<Int?>(null) }
    var openAnnotation by remember { mutableStateOf<List<GeniusAnnotation>?>(null) }
    LaunchedEffect(result, track?.id) {
        if (result is LyricsResult.Synced || result is LyricsResult.Plain) onRequestAnnotations()
    }
    openAnnotation?.let { list ->
        LyricsAnnotationSheet(annotations = list, accent = accent, onDismiss = { openAnnotation = null })
    }
    val annotationHaptic = rememberHapticTick()
    val showAnnotation: (List<GeniusAnnotation>) -> Unit = { list -> annotationHaptic(); openAnnotation = list }
    val cardLines = remember(result) {
        when (result) {
            is LyricsResult.Synced -> result.lines.map { it.text }
            is LyricsResult.Plain -> result.text.lines()
            else -> emptyList()
        }
    }
    val canShare = track != null && cardLines.any { it.isNotBlank() }
    val shareHaptic = rememberHapticTick()
    val openShare: (Int) -> Unit = { index -> if (canShare) { shareHaptic(); shareFrom = index } }
    val from = shareFrom
    if (from != null && track != null) {
        LyricsCardSheet(
            lines = cardLines,
            initialIndex = from,
            track = track,
            accent = accent,
            onDismiss = { shareFrom = null },
        )
    }
    val textStyle = if (large) {
        MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center)
    } else {
        MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (result) {
            null -> LoadingIndicator(color = accent)
            LyricsResult.NotFound -> LyricsEmptyState(onColor = onColor, mutedColor = mutedColor, surfaceColor = surfaceColor)
            is LyricsResult.Plain -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                if (canShare) {
                    LyricsSharePill(
                        onClick = { openShare(0) },
                        mutedColor = mutedColor,
                        surfaceColor = surfaceColor,
                        modifier = Modifier.padding(top = offsetControlTopPadding).align(Alignment.CenterHorizontally),
                    )
                }
                val linkStyle = TextLinkStyles(
                    style = SpanStyle(
                        background = accent.copy(alpha = 0.22f),
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                val plainText = remember(result.text, annotations, linkStyle) {
                    buildAnnotatedString {
                        result.text.lines().forEachIndexed { index, line ->
                            if (index > 0) append("\n")
                            val list = annotations[index]
                            if (list != null && line.isNotBlank()) {
                                withLink(
                                    LinkAnnotation.Clickable(
                                        tag = "annotation$index",
                                        styles = linkStyle,
                                        linkInteractionListener = { showAnnotation(list) },
                                    ),
                                ) { append(line) }
                            } else {
                                append(line)
                            }
                        }
                    }
                }
                Text(
                    plainText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 27.sp),
                    color = onColor.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { openShare(0) },
                        )
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                )
                LyricsSourceLabel(stringResource(R.string.player_lyrics_source, result.source), mutedColor)
            }
            is LyricsResult.Synced -> SyncedLyrics(
                lines = result.lines,
                activeIndex = activeIndex,
                onSeek = onSeek,
                sync = sync,
                onSyncAction = onSyncAction,
                accent = accent,
                onColor = onColor,
                mutedColor = mutedColor,
                surfaceColor = surfaceColor,
                positionMs = positionMs,
                isPlaying = isPlaying,
                textStyle = textStyle,
                offsetControlTopPadding = offsetControlTopPadding,
                canShare = canShare,
                openShare = openShare,
                annotations = annotations,
                onAnnotationClick = showAnnotation,
                frozen = openAnnotation != null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncedLyrics(
    lines: List<LyricsLine>,
    activeIndex: Int,
    onSeek: (Long) -> Unit,
    sync: LyricsSyncState,
    onSyncAction: (LyricsSyncAction) -> Unit,
    accent: Color,
    onColor: Color,
    mutedColor: Color,
    surfaceColor: Color,
    positionMs: Long,
    isPlaying: Boolean,
    textStyle: TextStyle,
    offsetControlTopPadding: Dp,
    canShare: Boolean,
    openShare: (Int) -> Unit,
    annotations: Map<Int, List<GeniusAnnotation>>,
    onAnnotationClick: (List<GeniusAnnotation>) -> Unit,
    frozen: Boolean,
) {
    val rows = remember(lines) { buildRows(lines) }
    val clock = rememberLyricsClock(positionMs, isPlaying, sync)
    val activeRow = remember(rows, activeIndex) {
        if (activeIndex < 0) rows.indexOfFirst { it.lineIndex == -1 } else rows.indexOfFirst { it.lineIndex == activeIndex }
    }

    Column(Modifier.fillMaxSize()) {
        var syncExpanded by rememberSaveable { mutableStateOf(false) }
        val shareAlpha by animateFloatAsState(if (syncExpanded) 0f else 1f, tween(180), label = "lyricsShareAlpha")
        LyricsSyncControls(
            state = sync,
            onAction = onSyncAction,
            mutedColor = mutedColor,
            surfaceColor = surfaceColor,
            accent = accent,
            expanded = syncExpanded,
            onExpandedChange = { syncExpanded = it },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = offsetControlTopPadding, bottom = 4.dp, start = 16.dp, end = 16.dp),
            trailing = if (canShare) {
                {
                    LyricsSharePill(
                        onClick = { openShare(activeIndex.coerceAtLeast(0)) },
                        mutedColor = mutedColor,
                        surfaceColor = surfaceColor,
                        enabled = !syncExpanded,
                        modifier = Modifier.graphicsLayer { alpha = shareAlpha },
                    )
                }
            } else null,
        )

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            var userBrowsing by remember(lines) { mutableStateOf(false) }
            var initialized by remember(lines) { mutableStateOf(false) }
            val viewportPx = with(LocalDensity.current) { maxHeight.toPx() }
            val focusPx = viewportPx * FOCUS_FRACTION

            LaunchedEffect(listState) {
                listState.interactionSource.interactions.collect { interaction ->
                    if (interaction is DragInteraction.Start) userBrowsing = true
                }
            }

            suspend fun focusOn(row: Int, animate: Boolean) {
                if (row < 0) return
                val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == row }
                if (animate && visible != null) {
                    val itemTop = visible.offset - listState.layoutInfo.viewportStartOffset
                    val delta = itemTop - focusPx
                    listState.animateScrollBy(
                        delta,
                        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                    )
                } else {
                    listState.scrollToItem(row)
                }
            }

            LaunchedEffect(userBrowsing, listState.isScrollInProgress, frozen) {
                if (frozen || !userBrowsing || listState.isScrollInProgress) return@LaunchedEffect
                kotlinx.coroutines.delay(3_500)
                userBrowsing = false
                focusOn(activeRow.coerceAtLeast(0), animate = true)
            }

            LaunchedEffect(activeRow, lines, viewportPx, frozen) {
                if (rows.isEmpty()) return@LaunchedEffect
                if ((userBrowsing || frozen) && initialized) return@LaunchedEffect
                focusOn(activeRow.coerceAtLeast(0), animate = initialized)
                initialized = true
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = maxHeight * FOCUS_FRACTION, bottom = maxHeight * 0.6f),
            ) {
                itemsIndexed(rows, key = { index, row -> "${row.lineIndex}:$index" }) { index, row ->
                    val distance = if (activeRow < 0) abs(index) else abs(index - activeRow)
                    when (row) {
                        is LyricsRow.Words -> LyricLine(
                            row = row,
                            clock = clock,
                            isActive = index == activeRow,
                            distance = distance,
                            browsing = userBrowsing,
                            textStyle = textStyle,
                            onColor = onColor,
                            mutedColor = mutedColor,
                            onClick = {
                                userBrowsing = false
                                onSeek(row.startMs)
                            },
                            onLongClick = { openShare(row.lineIndex) },
                            annotations = annotations[row.lineIndex],
                            onAnnotationClick = { list ->
                                userBrowsing = true
                                onAnnotationClick(list)
                            },
                        )
                        is LyricsRow.Interlude -> InterludeDots(
                            row = row,
                            clock = clock,
                            isActive = index == activeRow,
                            onColor = onColor,
                        )
                    }
                }
                item(key = "source") {
                    LyricsSourceLabel(stringResource(R.string.player_lyrics_source, "LRCLIB"), mutedColor)
                }
            }

            val activeAbove by remember(listState, activeRow) {
                derivedStateOf { listState.firstVisibleItemIndex > activeRow }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = userBrowsing,
                enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            ) {
                val haptic = rememberHapticTick()
                Surface(
                    onClick = {
                        haptic()
                        userBrowsing = false
                        scope.launch { listState.animateScrollToItem(activeRow.coerceAtLeast(0)) }
                    },
                    shape = RoundedCornerShape(50),
                    color = onColor,
                    contentColor = lerp(accent, Color.Black, 0.65f),
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Icon(
                            if (activeAbove) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.player_lyrics_back_to_current), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricLine(
    row: LyricsRow.Words,
    clock: State<Long>,
    isActive: Boolean,
    distance: Int,
    browsing: Boolean,
    textStyle: TextStyle,
    onColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    annotations: List<GeniusAnnotation>? = null,
    onAnnotationClick: (List<GeniusAnnotation>) -> Unit = {},
) {
    val haptic = rememberHapticTick()
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "lyricScale",
    )
    val baseColor by animateColorAsState(
        targetValue = when {
            isActive -> onColor.copy(alpha = 0.38f)
            browsing -> onColor.copy(alpha = 0.55f)
            else -> mutedColor.copy(alpha = (0.5f - 0.07f * distance).coerceAtLeast(0.18f))
        },
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        label = "lyricColor",
    )
    val blurRadius by animateDpAsState(
        targetValue = if (isActive || browsing) 0.dp else (distance.coerceAtMost(4) * 1.1f).dp,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        label = "lyricBlur",
    )
    val highlight by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = if (isActive) Spring.StiffnessHigh else Spring.StiffnessMedium),
        label = "lyricHighlight",
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val fillMs = remember(row) { fillDurationMs(row) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .then(if (Build.VERSION.SDK_INT >= 31 && blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { haptic(); onClick() },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        Text(
            row.text,
            style = textStyle,
            color = baseColor,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (annotations != null) {
                        Modifier.drawBehind {
                            val textLayout = layout ?: return@drawBehind
                            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                            for (line in 0 until textLayout.lineCount) {
                                val y = textLayout.getLineBottom(line) - 2.dp.toPx()
                                drawLine(
                                    color = onColor.copy(alpha = 0.35f),
                                    start = Offset(textLayout.getLineLeft(line), y),
                                    end = Offset(textLayout.getLineRight(line), y),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    pathEffect = dash,
                                )
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            onTextLayout = { layout = it },
        )
        if (highlight > 0f) {
            Text(
                row.text,
                style = textStyle,
                color = onColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = highlight
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val textLayout = layout ?: return@drawWithContent
                        val progress = ((clock.value - row.startMs).toFloat() / fillMs).coerceIn(0f, 1f)
                        if (progress >= 1f) return@drawWithContent
                        val covered = progress * row.text.length
                        val edge = 28.dp.toPx()
                        for (line in 0 until textLayout.lineCount) {
                            val start = textLayout.getLineStart(line)
                            val end = textLayout.getLineEnd(line, visibleEnd = true)
                            val top = textLayout.getLineTop(line) - 8f
                            val bottom = textLayout.getLineBottom(line) + 8f
                            if (covered >= end) continue
                            val x = if (covered <= start) {
                                textLayout.getLineLeft(line) - edge
                            } else {
                                val index = covered.toInt().coerceIn(start, end)
                                val fraction = covered - covered.toInt()
                                val x0 = textLayout.getHorizontalPosition(index, true)
                                val x1 = textLayout.getHorizontalPosition((index + 1).coerceAtMost(end), true)
                                x0 + (x1 - x0) * fraction
                            }
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black,
                                    startX = x - edge / 2f,
                                    endX = x + edge / 2f,
                                ),
                                topLeft = Offset(x - edge / 2f, top),
                                size = Size(size.width - x + edge + 64f, bottom - top),
                                blendMode = BlendMode.DstOut,
                            )
                        }
                    },
            )
        }
        val textLayout = layout
        if (annotations != null && textLayout != null && textLayout.lineCount > 0) {
            val last = textLayout.lineCount - 1
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (textLayout.getLineRight(last) + 4.dp.toPx()).roundToInt(),
                            ((textLayout.getLineTop(last) + textLayout.getLineBottom(last)) / 2f - 16.dp.toPx()).roundToInt(),
                        )
                    }
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onAnnotationClick(annotations) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .background(onColor.copy(alpha = 0.16f), CircleShape),
                ) {
                    Icon(
                        Icons.Filled.Lightbulb,
                        contentDescription = stringResource(R.string.lyrics_annotation_mark),
                        tint = onColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InterludeDots(
    row: LyricsRow.Interlude,
    clock: State<Long>,
    isActive: Boolean,
    onColor: Color,
) {
    val height by animateDpAsState(
        targetValue = if (isActive) 56.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "interludeHeight",
    )
    val appear by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium),
        label = "interludeAppear",
    )
    val transition = rememberInfiniteTransition(label = "interlude")
    val breathe by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (appear > 0.01f) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.graphicsLayer {
                    alpha = appear.coerceIn(0f, 1f)
                    val scale = appear * breathe
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                repeat(3) { dot ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .graphicsLayer {
                                val span = (row.endMs - row.startMs).coerceAtLeast(1L).toFloat()
                                val progress = ((clock.value - row.startMs) / span).coerceIn(0f, 1f)
                                val local = (progress * 3f - dot).coerceIn(0f, 1f)
                                alpha = 0.3f + 0.7f * local
                                val dotScale = 0.8f + 0.2f * local
                                scaleX = dotScale
                                scaleY = dotScale
                            }
                            .clip(CircleShape)
                            .background(onColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsSourceLabel(text: String, mutedColor: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = mutedColor.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsEmptyState(onColor: Color, mutedColor: Color, surfaceColor: Color) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided.normalized(), MaterialShapes.Clover4Leaf.normalized()) }
    val transition = rememberInfiniteTransition(label = "lyricsEmpty")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "morph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing)),
        label = "rotation",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(96.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(MorphingArtworkShape(morph, progress))
                    .background(surfaceColor),
            )
            Icon(Icons.Filled.SubtitlesOff, contentDescription = null, tint = onColor, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.player_lyrics_none_found),
            style = MaterialTheme.typography.titleMedium,
            color = mutedColor,
            textAlign = TextAlign.Center,
        )
    }
}
