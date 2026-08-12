package com.savoo.scclient.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import android.content.Intent
import com.savoo.scclient.R
import com.savoo.scclient.data.model.LyricsLine
import com.savoo.scclient.player.PlaybackState
import com.savoo.scclient.ui.haptics.rememberHapticTick
import com.savoo.scclient.ui.haptics.rememberHaptics
import com.savoo.scclient.ui.components.TrackArtwork
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun PlayerSheet(
    onArtistClick: (Long) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
    dockBar: @Composable () -> Unit,
) {
    val state by viewModel.controller.state.collectAsState()
    val glowColor by viewModel.controller.seedColor.collectAsState()
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }
    val track = state.currentTrack

    BackHandler(enabled = showFullPlayer) { showFullPlayer = false }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .navigationBarsPadding()
            // Extra clearance on top of the system nav-bar inset: on some devices (MIUI's gesture
            // nav in particular) touches near the very bottom edge get eaten by the OS gesture
            // recognizer before this composable ever sees them, even though the reported inset says
            // there's room - buttons placed right at that boundary can end up untappable.
            .padding(bottom = 8.dp),
    ) {
        Column {
            if (track != null) {
                MiniPlayerRow(
                    track = track,
                    state = state,
                    isFavorite = viewModel.isFavorite.collectAsState().value,
                    onExpand = { showFullPlayer = true },
                    onTogglePlay = { viewModel.controller.togglePlayPause() },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onNext = { viewModel.controller.skipToNext() },
                    onPrev = { viewModel.controller.skipToPrevious() },
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }
            dockBar()
        }
    }

    if (showFullPlayer && track != null) {
        FullPlayerSheet(
            state = state,
            isFavorite = viewModel.isFavorite.collectAsState().value,
            isOffline = viewModel.isOffline.collectAsState().value,
            isSavingOffline = viewModel.isSavingOffline.collectAsState().value,
            isMixPlaying = viewModel.isMixPlaying.collectAsState().value,
            glowColor = glowColor,
            lyrics = viewModel.lyrics.collectAsState().value,
            activeLyricsLine = viewModel.activeLyricsLine.collectAsState().value,
            lyricsOffsetMs = viewModel.lyricsOffsetMs.collectAsState().value,
            onAdjustLyricsOffset = { viewModel.adjustLyricsOffset(it) },
            onDismiss = { showFullPlayer = false },
            onTogglePlay = { viewModel.controller.togglePlayPause() },
            onScrubStart = { viewModel.controller.beginScrub() },
            onScrub = { viewModel.controller.scrubTo(it) },
            onSeek = { viewModel.controller.endScrub(it) },
            onToggleFavorite = { viewModel.toggleFavorite() },
            onSaveForOffline = { viewModel.saveForOffline() },
            onRemoveFromOffline = { viewModel.removeFromOffline() },
            onNext = { viewModel.controller.skipToNext() },
            onPrev = { viewModel.controller.skipToPrevious() },
            onToggleShuffle = { viewModel.controller.toggleShuffle() },
            onCycleRepeat = { viewModel.controller.cycleRepeatMode() },
            onArtistClick = { id -> showFullPlayer = false; onArtistClick(id) },
            onExcludeArtist = { viewModel.excludeCurrentArtistAndSkip() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullPlayerSheet(
    state: PlaybackState,
    isFavorite: Boolean,
    isOffline: Boolean,
    isSavingOffline: Boolean,
    isMixPlaying: Boolean,
    glowColor: Color?,
    lyrics: List<LyricsLine>?,
    activeLyricsLine: Int,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveForOffline: () -> Unit,
    onRemoveFromOffline: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onArtistClick: (Long) -> Unit = {},
    onExcludeArtist: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val palette = rememberPlayerPalette()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = palette.bg,
        dragHandle = null,
    ) {
        FullPlayerContent(
            state = state,
            isFavorite = isFavorite,
            isOffline = isOffline,
            isSavingOffline = isSavingOffline,
            isMixPlaying = isMixPlaying,
            glowColor = glowColor,
            lyrics = lyrics,
            activeLyricsLine = activeLyricsLine,
            lyricsOffsetMs = lyricsOffsetMs,
            onAdjustLyricsOffset = onAdjustLyricsOffset,
            onCollapse = onDismiss,
            onTogglePlay = onTogglePlay,
            onScrubStart = onScrubStart,
            onScrub = onScrub,
            onSeek = onSeek,
            onToggleFavorite = onToggleFavorite,
            onSaveForOffline = onSaveForOffline,
            onRemoveFromOffline = onRemoveFromOffline,
            onNext = onNext,
            onPrev = onPrev,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onArtistClick = onArtistClick,
            onExcludeArtist = onExcludeArtist,
        )
    }
}

private val PlayButtonShape = RoundedCornerShape(16.dp)

private val PlayerDarkBg = Color(0xFF17181A)
private val PlayerCardDim = Color(0xFF232420)
private val PlayerOnDark = Color(0xFFF5F3E7)
private val PlayerOnDarkMuted = Color(0xFFAFAEA2)

// The main play button always sits on top of `accent`, which is itself blended heavily toward
// white (see FullPlayerContent), so its content stays dark regardless of light/dark theme.
private val PlayerAccentInk = Color(0xFF17181A)

private data class PlayerPalette(val bg: Color, val card: Color, val on: Color, val onMuted: Color)

// The big player uses a bespoke dark palette (not tied to the seed/dynamic color theme) for its
// dark-mode look, but in light mode it should just match every other surface in the app - so the
// light variant pulls straight from MaterialTheme instead of a second hand-picked palette.
// `background`'s luminance mirrors the app's light/dark setting exactly (see Theme.kt's
// buildDarkScheme/buildLightScheme/buildSeedScheme), so it's a reliable signal here without
// threading the dark/light flag through every composable in this file.
@Composable
private fun rememberPlayerPalette(): PlayerPalette {
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.background.luminance() > 0.5f
    return if (isLight) {
        PlayerPalette(scheme.background, scheme.surfaceContainerHigh, scheme.onSurface, scheme.onSurfaceVariant)
    } else {
        PlayerPalette(PlayerDarkBg, PlayerCardDim, PlayerOnDark, PlayerOnDarkMuted)
    }
}

private fun Color.tone(amount: Float, towards: Color): Color = lerp(this, towards, amount)

private const val BlobAmplitude = 0.055f
private const val BlobBumps = 8
private const val BlobPhase = 0f

// Shared by BlobShape's clip outline and the artwork's progress ring, so the ring traces the exact
// same wobbly contour as the artwork clip, just at a bigger size (and, for the ring, rotated to
// start at the top like a clock).
private fun buildBlobPath(
    size: Size,
    amplitude: Float = BlobAmplitude,
    bumps: Int = BlobBumps,
    phase: Float = BlobPhase,
    startAngleOffset: Float = 0f,
): Path {
    val path = Path()
    val cx = size.width / 2f
    val cy = size.height / 2f
    // Divided by (1 + amplitude) so the outward bumps (sin peak = 1) top out exactly at the
    // box's own half-size instead of overshooting it - otherwise the clip cuts the bumps flush
    // with the box edge, showing as flattened, "eaten-away" notches instead of a smooth curve.
    val baseR = minOf(size.width, size.height) / 2f / (1f + amplitude)
    val segments = 128
    for (i in 0..segments) {
        val t = i.toFloat() / segments
        val theta = startAngleOffset + t * 2f * Math.PI.toFloat()
        val r = baseR * (1f + amplitude * sin(bumps * theta + phase))
        val x = cx + r * cos(theta)
        val y = cy + r * sin(theta)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private class BlobShape(
    private val amplitude: Float = BlobAmplitude,
    private val bumps: Int = BlobBumps,
    private val phase: Float = BlobPhase,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(buildBlobPath(size, amplitude, bumps, phase))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniPlayerRow(
    track: com.savoo.scclient.data.model.Track,
    state: PlaybackState,
    isFavorite: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    val haptic = rememberHapticTick()
    val haptics = rememberHaptics()
    var heartAnimating by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartAnimating) 1.4f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "miniHeart",
        finishedListener = { heartAnimating = false }
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "miniPlayerPress",
    )

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "miniPlayButtonScale",
    )

    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300, easing = LinearEasing),
        label = "miniPlayerProgress",
    )

    Column(
        modifier = Modifier
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = null) { haptic(); onExpand() },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TrackArtwork(
                artworkUrl = track.artworkUrl,
                contentDescription = null,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    track.user.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.hasPrev) {
                IconButton(onClick = { haptic(); onPrev() }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Surface(
                onClick = { haptics.click(); onTogglePlay() },
                interactionSource = playInteractionSource,
                shape = PlayButtonShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(42.dp)
                    .scale(playScale),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.isBuffering) {
                        LoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            if (state.hasNext) {
                IconButton(onClick = { haptic(); onNext() }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = { haptics.click(); heartAnimating = true; onToggleFavorite() }, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.scale(heartScale).size(20.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 5.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerContent(
    state: PlaybackState,
    isFavorite: Boolean,
    isOffline: Boolean,
    isSavingOffline: Boolean,
    isMixPlaying: Boolean,
    glowColor: Color?,
    lyrics: List<LyricsLine>?,
    activeLyricsLine: Int,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveForOffline: () -> Unit,
    onRemoveFromOffline: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onArtistClick: (Long) -> Unit = {},
    onExcludeArtist: () -> Unit = {},
) {
    val context = LocalContext.current
    val palette = rememberPlayerPalette()
    val haptic = rememberHapticTick()
    val haptics = rememberHaptics()
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var lastSeekTickSecond by remember { mutableStateOf(-1L) }
    var heartAnimating by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartAnimating) 1.4f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "heartFull",
        finishedListener = { heartAnimating = false }
    )
    val playScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "playFull"
    )

    val themeAccent = MaterialTheme.colorScheme.primary
    val accent by animateColorAsState(
        targetValue = (glowColor ?: themeAccent).tone(0.55f, Color.White),
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "playerAccent",
    )

    var prevTrackId by remember { mutableStateOf(state.currentTrack?.id) }
    val slideOffset = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(Unit) {
        snapshotFlow { state.currentTrack?.id }
            .distinctUntilChanged()
            .collect { newId ->
                if (prevTrackId != null && newId != prevTrackId) {
                    val dir = if (newId!! > (prevTrackId ?: 0)) 1 else -1
                    slideOffset.snapTo(dir * 300f)
                    slideOffset.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    )
                }
                prevTrackId = newId
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { haptic(); onCollapse() }) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_collapse),
                    modifier = Modifier.size(32.dp),
                    tint = palette.on,
                )
            }
            Row {
                IconButton(onClick = { haptic(); showLyrics = !showLyrics }) {
                    Icon(
                        Icons.Filled.Lyrics,
                        contentDescription = stringResource(R.string.player_lyrics),
                        modifier = Modifier.size(24.dp),
                        tint = if (showLyrics) accent else palette.on,
                    )
                }
                IconButton(onClick = {
                    haptic()
                    state.currentTrack?.permalinkUrl?.let { url ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                }) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.player_share),
                        modifier = Modifier.size(24.dp),
                        tint = palette.on,
                    )
                }
            }
        }

        Crossfade(
            targetState = showLyrics,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "playerMiddle",
        ) { lyricsMode ->
            if (lyricsMode) {
                LyricsView(
                    lines = lyrics,
                    activeIndex = activeLyricsLine,
                    onSeek = onSeek,
                    offsetMs = lyricsOffsetMs,
                    onAdjustOffset = onAdjustLyricsOffset,
                    accent = accent,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                    ) {
                        val artSize = minOf(maxWidth * 0.92f, maxHeight).coerceAtLeast(120.dp)
                        ArtworkOrb(
                            artworkUrl = state.currentTrack?.artworkUrl,
                            glowColor = glowColor,
                            isPlaying = state.isPlaying,
                            progress = if (state.durationMs > 0) {
                                ((if (isDragging) dragPosition else state.positionMs.toFloat()) / state.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f,
                            slideOffsetX = slideOffset,
                            modifier = Modifier
                                .size(artSize)
                                .align(Alignment.BottomCenter),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    state.currentTrack?.let { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(slideOffset.value.roundToInt(), 0) },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.player_playing_from, playingFromSource(state.queueTag, track.user.username)),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.onMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isMixPlaying) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    onClick = { haptic(); onExcludeArtist() },
                                    shape = RoundedCornerShape(50),
                                    color = palette.card,
                                    contentColor = palette.onMuted,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.PersonOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            stringResource(R.string.player_exclude_artist_short),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(slideOffset.value.roundToInt(), 0) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        state.currentTrack?.let { track ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = palette.on,
                                )
                                Text(
                                    track.user.username,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.onMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { haptic(); onArtistClick(track.user.id) },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { haptics.click(); heartAnimating = true; onToggleFavorite() }) {
                            Icon(
                                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) accent else palette.onMuted,
                                modifier = Modifier.scale(heartScale),
                            )
                        }
                        if (isSavingOffline) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = accent,
                                )
                            }
                        } else if (isOffline) {
                            IconButton(onClick = { haptic(); onRemoveFromOffline() }) {
                                Icon(
                                    Icons.Filled.CloudDone,
                                    contentDescription = stringResource(R.string.player_saved_offline),
                                    tint = accent,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        } else {
                            IconButton(onClick = { haptic(); onSaveForOffline() }) {
                                Icon(
                                    Icons.Filled.CloudDownload,
                                    contentDescription = stringResource(R.string.player_save_offline),
                                    tint = palette.onMuted,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Column {
            Slider(
                value = if (isDragging) dragPosition else state.positionMs.toFloat(),
                onValueChange = { value ->
                    val second = (value / 1000L).toLong()
                    if (!isDragging) {
                        haptics.seekEdge()
                        lastSeekTickSecond = second
                        onScrubStart()
                    } else if (second != lastSeekTickSecond) {
                        haptics.seekTick()
                        lastSeekTickSecond = second
                    }
                    dragPosition = value
                    isDragging = true
                    onScrub(value.roundToLong())
                },
                onValueChangeFinished = {
                    haptics.seekEdge()
                    onSeek(dragPosition.roundToLong())
                    isDragging = false
                },
                valueRange = 0f..(state.durationMs.coerceAtLeast(1L)).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = palette.onMuted.copy(alpha = 0.25f),
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTime(if (isDragging) dragPosition.toLong() else state.positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onMuted,
                )
                Text(
                    formatTime(state.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onMuted,
                )
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                onClick = { haptics.click(); onTogglePlay() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale },
                shape = RoundedCornerShape(50),
                color = accent,
                contentColor = PlayerAccentInk,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.isBuffering || state.loadingTrackId != null) {
                        LoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            color = PlayerAccentInk,
                        )
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerCircleIconButton(
                    icon = Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.player_shuffle),
                    onClick = onToggleShuffle,
                    tint = if (state.shuffleEnabled) accent else palette.onMuted,
                )
                PlayerCircleIconButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = null,
                    onClick = onPrev,
                    tint = palette.on,
                )
                PlayerCircleIconButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = null,
                    onClick = onNext,
                    tint = palette.on,
                )
                PlayerCircleIconButton(
                    icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = stringResource(R.string.player_repeat),
                    onClick = onCycleRepeat,
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else palette.onMuted,
                )
            }
        }
    }
}

@Composable
private fun PlayerCircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 24.dp,
) {
    val palette = rememberPlayerPalette()
    val haptic = rememberHapticTick()
    IconButton(
        onClick = { haptic(); onClick() },
        modifier = modifier.size(size),
        colors = IconButtonDefaults.iconButtonColors(containerColor = palette.card, contentColor = tint),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtworkOrb(
    artworkUrl: String?,
    glowColor: Color?,
    isPlaying: Boolean,
    progress: Float,
    slideOffsetX: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    val fallback = MaterialTheme.colorScheme.primary
    val colorSpec = tween<Color>(1400, easing = FastOutSlowInEasing)
    val orbA by animateColorAsState(glowColor ?: fallback, colorSpec, label = "orbColorA")
    val orbB by animateColorAsState(
        glowColor?.let { lerp(it, MaterialTheme.colorScheme.tertiary, 0.55f) } ?: MaterialTheme.colorScheme.tertiary,
        colorSpec,
        label = "orbColorB",
    )
    fun softGradient(c: Color) = Brush.radialGradient(
        0f to c,
        0.55f to c.copy(alpha = c.alpha * 0.55f),
        1f to c.copy(alpha = 0f),
    )

    // Collapses the orb down to a faint speck when paused, and blooms it back out when playback resumes.
    val presence by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.1f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "orbPresence",
    )
    val presenceAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.3f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "orbPresenceAlpha",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val speed = if (isPlaying) 1f else 0.35f
    val angleA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween((14000 / speed).toInt(), easing = LinearEasing),
        ),
        label = "orbAngleA",
    )
    val angleB by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween((19000 / speed).toInt(), easing = LinearEasing),
        ),
        label = "orbAngleB",
    )
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween((4200 / speed).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbBreathe",
    )

    val blobShape = remember { BlobShape() }
    val ringColor = orbA.tone(0.45f, Color.White)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300, easing = LinearEasing),
        label = "artworkRingProgress",
    )

    // requiredSize lets the orb overflow past the artwork's own bounds (unlike fillMaxWidth, it isn't clamped to the parent's constraints).
    // The inner Box is pinned to exactly artSize so that overflow, so it doesn't push the title/controls below it further down the screen.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val artSize = maxWidth
        val orbSize = artSize * 1.1f
        val ringGap = 14.dp
        val ringStroke = 4.dp
        val ringSize = artSize + ringGap * 2 + ringStroke
        Box(modifier = Modifier.size(artSize), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .requiredSize(orbSize)
                    .graphicsLayer {
                        scaleX = breathe * presence; scaleY = breathe * presence
                        alpha = presenceAlpha
                        val rad = Math.toRadians(angleA.toDouble())
                        translationX = (cos(rad) * 70f).toFloat()
                        translationY = (sin(rad) * 70f).toFloat()
                    }
                    .background(softGradient(orbA.copy(alpha = 1f)), CircleShape)
                    .blur(46.dp, BlurredEdgeTreatment.Unbounded)
            )
            Box(
                modifier = Modifier
                    .requiredSize(orbSize * 0.85f)
                    .graphicsLayer {
                        scaleX = breathe * presence; scaleY = breathe * presence
                        alpha = presenceAlpha
                        val rad = Math.toRadians(angleB.toDouble())
                        translationX = (cos(rad) * 65f).toFloat()
                        translationY = (sin(rad) * 65f).toFloat()
                    }
                    .background(softGradient(orbB.copy(alpha = 1f)), CircleShape)
                    .blur(46.dp, BlurredEdgeTreatment.Unbounded)
            )
            TrackArtwork(
                artworkUrl = artworkUrl,
                contentDescription = null,
                shape = blobShape,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(slideOffsetX.value.roundToInt(), 0) },
            )
            Canvas(modifier = Modifier.requiredSize(ringSize)) {
                val strokePx = ringStroke.toPx()
                val inset = strokePx / 2f
                val pathBox = Size(size.width - strokePx, size.height - strokePx)
                val ringPath = buildBlobPath(pathBox, startAngleOffset = -(Math.PI / 2).toFloat())
                ringPath.translate(Offset(inset, inset))

                val measure = PathMeasure()
                measure.setPath(ringPath, forceClosed = true)
                val progressPath = Path()
                measure.getSegment(0f, measure.length * animatedProgress, progressPath, startWithMoveTo = true)

                val strokeStyle = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(ringPath, color = ringColor.copy(alpha = 0.25f), style = strokeStyle)
                drawPath(progressPath, color = ringColor, style = strokeStyle)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsView(
    lines: List<LyricsLine>?,
    activeIndex: Int,
    onSeek: (Long) -> Unit,
    offsetMs: Long,
    onAdjustOffset: (Long) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val palette = rememberPlayerPalette()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            lines == null -> LoadingIndicator(color = accent)
            lines.isEmpty() -> Text(
                stringResource(R.string.player_lyrics_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            else -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val listState = rememberLazyListState()
                    val viewportPx = with(LocalDensity.current) { maxHeight.toPx() }

                    // Jump straight to wherever playback already is when a new track's lyrics arrive - the player
                    // screen can stay open across track changes and lyrics can be opened mid-song, and without this
                    // the list either kept the previous track's scroll offset or sat at the top until the next line.
                    LaunchedEffect(lines) {
                        listState.scrollToItem(
                            index = activeIndex.coerceAtLeast(0),
                            scrollOffset = -(viewportPx * 0.4f).toInt(),
                        )
                    }

                    LaunchedEffect(activeIndex, lines) {
                        if (activeIndex >= 0) {
                            listState.animateScrollToItem(
                                index = activeIndex,
                                scrollOffset = -(viewportPx * 0.4f).toInt(),
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = maxHeight * 0.4f, horizontal = 8.dp),
                    ) {
                        itemsIndexed(lines) { index, line ->
                            LyricsLineItem(
                                text = line.text,
                                isActive = index == activeIndex,
                                isPast = index < activeIndex,
                                onClick = { onSeek(line.timeMs) },
                            )
                        }
                    }

                    // Community-sourced lyrics timing can be off by a fixed amount for a given track; this lets the
                    // user nudge it back in sync instead of just living with words landing early or late.
                    LyricsOffsetControl(
                        offsetMs = offsetMs,
                        onAdjustOffset = onAdjustOffset,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsOffsetControl(
    offsetMs: Long,
    onAdjustOffset: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberPlayerPalette()
    val haptic = rememberHapticTick()
    Surface(
        color = palette.card.copy(alpha = 0.9f),
        contentColor = palette.onMuted,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(onClick = { haptic(); onAdjustOffset(-500L) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.player_lyrics_offset_earlier), modifier = Modifier.size(18.dp))
            }
            Text(
                text = "%+.1fs".format(offsetMs / 1000f),
                style = MaterialTheme.typography.labelMedium,
                color = palette.onMuted,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = offsetMs != 0L,
                    ) { haptic(); onAdjustOffset(-offsetMs) },
            )
            IconButton(onClick = { haptic(); onAdjustOffset(500L) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.player_lyrics_offset_later), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LyricsLineItem(
    text: String,
    isActive: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
) {
    val palette = rememberPlayerPalette()
    val haptic = rememberHapticTick()
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "lyricScale",
    )
    val color by animateColorAsState(
        targetValue = when {
            isActive -> palette.on
            isPast -> palette.onMuted.copy(alpha = 0.35f)
            else -> palette.onMuted.copy(alpha = 0.6f)
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "lyricColor",
    )
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { haptic(); onClick() }
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun playingFromSource(queueTag: String?, artistName: String): String = when {
    queueTag == null -> artistName
    queueTag == "home_mix" -> stringResource(R.string.player_source_mix)
    queueTag == "favorites" -> stringResource(R.string.player_source_favorites)
    queueTag == "offline" -> stringResource(R.string.player_source_offline)
    queueTag == "recent" -> stringResource(R.string.home_section_jump_back_in)
    queueTag == "search" -> stringResource(R.string.player_source_search)
    queueTag.startsWith("artist:") -> queueTag.removePrefix("artist:")
    queueTag.startsWith("playlist:") -> queueTag.removePrefix("playlist:")
    else -> artistName
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
