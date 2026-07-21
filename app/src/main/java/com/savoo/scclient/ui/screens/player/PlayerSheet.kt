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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.FilledIconToggleButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
) {
    val state by viewModel.controller.state.collectAsState()
    val glowColor by viewModel.controller.seedColor.collectAsState()
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }

    val track = state.currentTrack ?: return

    BackHandler(enabled = showFullPlayer) { showFullPlayer = false }

    MiniPlayer(
        state = state,
        isFavorite = viewModel.isFavorite.collectAsState().value,
        onExpand = { showFullPlayer = true },
        onTogglePlay = { viewModel.controller.togglePlayPause() },
        onToggleFavorite = { viewModel.toggleFavorite() },
        onNext = { viewModel.controller.skipToNext() },
        onPrev = { viewModel.controller.skipToPrevious() },
        onArtistClick = onArtistClick,
    )

    if (showFullPlayer) {
        FullPlayerSheet(
            state = state,
            isFavorite = viewModel.isFavorite.collectAsState().value,
            isOffline = viewModel.isOffline.collectAsState().value,
            isSavingOffline = viewModel.isSavingOffline.collectAsState().value,
            glowColor = glowColor,
            lyrics = viewModel.lyrics.collectAsState().value,
            activeLyricsLine = viewModel.activeLyricsLine.collectAsState().value,
            lyricsOffsetMs = viewModel.lyricsOffsetMs.collectAsState().value,
            onAdjustLyricsOffset = { viewModel.adjustLyricsOffset(it) },
            onDismiss = { showFullPlayer = false },
            onTogglePlay = { viewModel.controller.togglePlayPause() },
            onSeek = { viewModel.controller.seekTo(it) },
            onToggleFavorite = { viewModel.toggleFavorite() },
            onSaveForOffline = { viewModel.saveForOffline() },
            onRemoveFromOffline = { viewModel.removeFromOffline() },
            onNext = { viewModel.controller.skipToNext() },
            onPrev = { viewModel.controller.skipToPrevious() },
            onToggleShuffle = { viewModel.controller.toggleShuffle() },
            onCycleRepeat = { viewModel.controller.cycleRepeatMode() },
            onArtistClick = { id -> showFullPlayer = false; onArtistClick(id) },
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
    glowColor: Color?,
    lyrics: List<LyricsLine>?,
    activeLyricsLine: Int,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveForOffline: () -> Unit,
    onRemoveFromOffline: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onArtistClick: (Long) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        FullPlayerContent(
            state = state,
            isFavorite = isFavorite,
            isOffline = isOffline,
            isSavingOffline = isSavingOffline,
            glowColor = glowColor,
            lyrics = lyrics,
            activeLyricsLine = activeLyricsLine,
            lyricsOffsetMs = lyricsOffsetMs,
            onAdjustLyricsOffset = onAdjustLyricsOffset,
            onCollapse = onDismiss,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onToggleFavorite = onToggleFavorite,
            onSaveForOffline = onSaveForOffline,
            onRemoveFromOffline = onRemoveFromOffline,
            onNext = onNext,
            onPrev = onPrev,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onArtistClick = onArtistClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniPlayer(
    state: PlaybackState,
    isFavorite: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onArtistClick: (Long) -> Unit = {},
) {
    val track = state.currentTrack ?: return
    var heartAnimating by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartAnimating) 1.4f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "miniHeart",
        finishedListener = { heartAnimating = false }
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .navigationBarsPadding()
            .clickable { onExpand() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TrackArtwork(
                artworkUrl = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
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
                IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(44.dp)) {
                if (state.isBuffering) {
                    LoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            if (state.hasNext) {
                IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = { heartAnimating = true; onToggleFavorite() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.scale(heartScale),
                )
            }
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
    glowColor: Color?,
    lyrics: List<LyricsLine>?,
    activeLyricsLine: Int,
    lyricsOffsetMs: Long,
    onAdjustLyricsOffset: (Long) -> Unit,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveForOffline: () -> Unit,
    onRemoveFromOffline: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onArtistClick: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
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
            IconButton(onClick = onCollapse) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_collapse),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row {
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        Icons.Filled.Lyrics,
                        contentDescription = stringResource(R.string.player_lyrics),
                        modifier = Modifier.size(24.dp),
                        tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = {
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
                        tint = MaterialTheme.colorScheme.onSurface,
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
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ArtworkOrb(
                        artworkUrl = state.currentTrack?.artworkUrl,
                        glowColor = glowColor,
                        isPlaying = state.isPlaying,
                        slideOffsetX = slideOffset,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(slideOffset.value.roundToInt(), 0) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        state.currentTrack?.let { track ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    track.user.username,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { onArtistClick(track.user.id) },
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { heartAnimating = true; onToggleFavorite() }) {
                            Icon(
                                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.scale(heartScale),
                            )
                        }
                        if (isSavingOffline) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else if (isOffline) {
                            IconButton(onClick = onRemoveFromOffline) {
                                Icon(
                                    Icons.Filled.CloudDone,
                                    contentDescription = stringResource(R.string.player_saved_offline),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        } else {
                            IconButton(onClick = onSaveForOffline) {
                                Icon(
                                    Icons.Filled.CloudDownload,
                                    contentDescription = stringResource(R.string.player_save_offline),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        Column {
            Slider(
                value = if (isDragging) dragPosition else state.positionMs.toFloat(),
                onValueChange = { dragPosition = it; isDragging = true },
                onValueChangeFinished = { onSeek(dragPosition.roundToLong()); isDragging = false },
                valueRange = 0f..(state.durationMs.coerceAtLeast(1L)).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTime(if (isDragging) dragPosition.toLong() else state.positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatTime(state.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconToggleButton(
                    checked = state.shuffleEnabled,
                    onCheckedChange = { onToggleShuffle() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Surface(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.isBuffering || state.loadingTrackId != null) {
                            LoadingIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp),
                    )
                }
                FilledIconToggleButton(
                    checked = state.repeatMode != Player.REPEAT_MODE_OFF,
                    onCheckedChange = { onCycleRepeat() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = stringResource(R.string.player_repeat),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtworkOrb(
    artworkUrl: String?,
    glowColor: Color?,
    isPlaying: Boolean,
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

    // requiredSize lets the orb overflow past the artwork's own bounds (unlike fillMaxWidth, it isn't clamped to the parent's constraints).
    // The inner Box is pinned to exactly artSize so that overflow, so it doesn't push the title/controls below it further down the screen.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val artSize = maxWidth
        val orbSize = artSize * 1.28f
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
                    .background(softGradient(orbA.copy(alpha = 0.95f)), CircleShape)
                    .blur(80.dp, BlurredEdgeTreatment.Unbounded)
            )
            Box(
                modifier = Modifier
                    .requiredSize(orbSize * 0.82f)
                    .graphicsLayer {
                        scaleX = breathe * presence; scaleY = breathe * presence
                        alpha = presenceAlpha
                        val rad = Math.toRadians(angleB.toDouble())
                        translationX = (cos(rad) * 65f).toFloat()
                        translationY = (sin(rad) * 65f).toFloat()
                    }
                    .background(softGradient(orbB.copy(alpha = 0.85f)), CircleShape)
                    .blur(80.dp, BlurredEdgeTreatment.Unbounded)
            )
            TrackArtwork(
                artworkUrl = artworkUrl,
                contentDescription = null,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(slideOffsetX.value.roundToInt(), 0) },
            )
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
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            lines == null -> LoadingIndicator(color = MaterialTheme.colorScheme.primary)
            lines.isEmpty() -> Text(
                stringResource(R.string.player_lyrics_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            else -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val listState = rememberLazyListState()
                    val viewportPx = with(LocalDensity.current) { maxHeight.toPx() }
                    // Scoped to `lines`, so it's freshly true again for every new track: the first activeIndex we see
                    // for a track is skipped (stay at the top instead of jumping straight to wherever playback already
                    // is), and only subsequent index changes - i.e. the song actually progressing - animate-follow.
                    var skipNextAutoScroll by remember(lines) { mutableStateOf(true) }

                    // Unconditional reset to the top whenever a new track's lyrics arrive - the player screen can stay
                    // open across track changes, and without this the list just kept whatever scroll offset the
                    // previous (often longer) track's lyrics had left it at.
                    LaunchedEffect(lines) {
                        listState.scrollToItem(0, 0)
                    }

                    LaunchedEffect(activeIndex, lines) {
                        if (skipNextAutoScroll) {
                            skipNextAutoScroll = false
                            return@LaunchedEffect
                        }
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(onClick = { onAdjustOffset(-500L) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.player_lyrics_offset_earlier), modifier = Modifier.size(18.dp))
            }
            Text(
                text = "%+.1fs".format(offsetMs / 1000f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = offsetMs != 0L,
                    ) { onAdjustOffset(-offsetMs) },
            )
            IconButton(onClick = { onAdjustOffset(500L) }, modifier = Modifier.size(32.dp)) {
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
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "lyricScale",
    )
    val color by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.onSurface
            isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
            ) { onClick() }
            .padding(vertical = 10.dp),
    )
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
