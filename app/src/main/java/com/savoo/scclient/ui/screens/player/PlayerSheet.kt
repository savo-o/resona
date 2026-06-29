package com.savoo.scclient.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.savoo.scclient.player.PlaybackState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@UnstableApi
@Composable
fun PlayerSheet(
    onArtistClick: (Long) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.controller.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }

    val track = state.currentTrack ?: return
    val density = LocalDensity.current

    BackHandler(enabled = expanded) { expanded = false }

    val expandProgress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        expandProgress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        )
    }

    val screenHeightPx = with(density) { 600.dp.roundToPx() }
    val fullPlayerOffsetY = ((1f - expandProgress.value) * screenHeightPx).toInt()
    val miniPlayerAlpha = (1f - expandProgress.value * 2f).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        if (expanded || expandProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, fullPlayerOffsetY) }
                    .graphicsLayer { alpha = expandProgress.value }
            ) {
                FullPlayer(
                    state = state,
                    isFavorite = viewModel.isFavorite.collectAsState().value,
                    onCollapse = { expanded = false },
                    onTogglePlay = { viewModel.controller.togglePlayPause() },
                    onSeek = { viewModel.controller.seekTo(it) },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onNext = { viewModel.controller.skipToNext() },
                    onPrev = { viewModel.controller.skipToPrevious() },
                    onArtistClick = { id -> expanded = false; onArtistClick(id) },
                )
            }
        }

        if (!expanded || expandProgress.value < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = miniPlayerAlpha }
            ) {
                MiniPlayer(
                    state = state,
                    isFavorite = viewModel.isFavorite.collectAsState().value,
                    onExpand = { expanded = true },
                    onTogglePlay = { viewModel.controller.togglePlayPause() },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onNext = { viewModel.controller.skipToNext() },
                    onPrev = { viewModel.controller.skipToPrevious() },
                    onArtistClick = onArtistClick,
                )
            }
        }
    }
}

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
            AsyncImage(
                model = track.artworkUrl?.replace("-large", "-t200x200"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
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
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onArtistClick(track.user.id) },
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
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

@Composable
private fun FullPlayer(
    state: PlaybackState,
    isFavorite: Boolean,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onArtistClick: (Long) -> Unit = {},
) {
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
        targetValue = if (state.isPlaying) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "playFull"
    )

    var prevTrackId by remember { mutableStateOf(state.currentTrack?.id) }
    val slideOffset = remember { Animatable(0f) }

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

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .offset { IntOffset(slideOffset.value.roundToInt(), 0) },
                contentAlignment = Alignment.Center
            ) {
                state.currentTrack?.let { track ->
                    AsyncImage(
                        model = track.artworkUrl?.replace("-large", "-t500x500"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(32.dp))
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .offset { IntOffset(slideOffset.value.roundToInt(), 0) }
            ) {
                state.currentTrack?.let { track ->
                    Column {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            track.user.username,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onArtistClick(track.user.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.05f))

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
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { heartAnimating = true; onToggleFavorite() }) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.scale(heartScale),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPrev) {
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
                        .size(60.dp)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
