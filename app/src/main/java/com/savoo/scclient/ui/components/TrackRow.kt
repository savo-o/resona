package com.savoo.scclient.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.UnavailableReason

/**
 * Shared artwork tile: shows a music-note placeholder behind the real image, so tracks with no
 * artwork (e.g. Telegram-imported offline tracks with no source to pull a cover from) or artwork
 * that fails to load (e.g. no network while playing offline) get a real card instead of blank space.
 */
@Composable
fun TrackArtwork(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl.replace("-large", "-t500x500"),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Where a favorited track came from, shown as a small badge on its artwork in the Favorites list. */
enum class FavoriteSource { LOCAL, ONLINE, BOTH }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    isLoading: Boolean = false,
    isPlaying: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onTogglePlayPause: (() -> Unit)? = null,
    favoriteSource: FavoriteSource? = null,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null,
    selectionActive: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    unavailableReason: UnavailableReason? = null,
) {
    val haptic = com.savoo.scclient.ui.haptics.rememberHapticTick()
    val haptics = com.savoo.scclient.ui.haptics.rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "trackRowScale"
    )

    var heartAnimating by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartAnimating) 1.4f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "heartScale",
        finishedListener = { heartAnimating = false }
    )

    var burstHeart by remember { mutableStateOf(false) }
    LaunchedEffect(burstHeart) {
        if (burstHeart) {
            kotlinx.coroutines.delay(600)
            burstHeart = false
        }
    }

    var optimisticDownload by remember { mutableStateOf(false) }
    LaunchedEffect(isDownloading, isDownloaded) {
        if (isDownloading || isDownloaded) optimisticDownload = false
    }
    LaunchedEffect(optimisticDownload) {
        if (optimisticDownload) {
            kotlinx.coroutines.delay(4000)
            optimisticDownload = false
        }
    }
    val showDownloadProgress = isDownloading || optimisticDownload

    fun handleRowTap() {
        if (selectionActive) onLongPress?.invoke() else { haptic(); onClick() }
    }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "trackRowContainerColor",
    )

    val rowContent: @Composable () -> Unit = {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { handleRowTap() },
                    onLongClick = onLongPress,
                ),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArtworkWithOverlays(
                    track = track,
                    isPlaying = isPlaying,
                    favoriteSource = favoriteSource,
                    selectionActive = selectionActive,
                    isSelected = isSelected,
                    burstHeart = burstHeart,
                    unavailableReason = unavailableReason,
                    onTap = { handleRowTap() },
                    onLongPress = onLongPress,
                    onDoubleTapLike = {
                        if (onToggleFavorite != null && !selectionActive) {
                            burstHeart = true
                            if (!isFavorite) {
                                haptics.like()
                                heartAnimating = true
                                onToggleFavorite()
                            }
                        }
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (unavailableReason != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (unavailableReason != null) stringResource(R.string.track_unavailable) else track.user.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onToggleDownload != null && isDownloaded && !showDownloadProgress) {
                    ConfirmOfflineRemoval(onRemove = onToggleDownload) { armed, onPress, bounce ->
                        IconButton(onClick = onPress, modifier = bounce) {
                            Icon(
                                imageVector = if (armed) Icons.Filled.Delete else Icons.Filled.CloudDone,
                                contentDescription = stringResource(R.string.action_download),
                                tint = if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else if (onToggleDownload != null) {
                    IconButton(
                        onClick = {
                            if (!showDownloadProgress) {
                                haptics.click()
                                if (!isDownloaded) optimisticDownload = true
                                onToggleDownload()
                            }
                        },
                    ) {
                        if (showDownloadProgress) {
                            LoadingIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Filled.CloudDone else Icons.Filled.CloudDownload,
                                contentDescription = stringResource(R.string.action_download),
                                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (onToggleFavorite != null) {
                    IconButton(onClick = {
                        haptics.like()
                        heartAnimating = true
                        onToggleFavorite()
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.action_favorite),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.scale(heartScale)
                        )
                    }
                }
                Surface(
                    onClick = { haptics.click(); onTogglePlayPause?.invoke() ?: onClick() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    if (isLoading) {
                        LoadingIndicator(
                            modifier = Modifier.padding(8.dp).size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    Box(modifier = modifier) { rowContent() }
}

@Composable
private fun ArtworkWithOverlays(
    track: Track,
    isPlaying: Boolean,
    favoriteSource: FavoriteSource?,
    selectionActive: Boolean,
    isSelected: Boolean,
    burstHeart: Boolean,
    unavailableReason: UnavailableReason?,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
    onDoubleTapLike: () -> Unit,
) {
    Box(
        modifier = Modifier.pointerInput(track.id, selectionActive) {
            detectTapGestures(
                onTap = { onTap() },
                onLongPress = { onLongPress?.invoke() },
                onDoubleTap = { onDoubleTapLike() },
            )
        },
    ) {
        TrackArtwork(
            artworkUrl = track.artworkUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(56.dp)
                .then(if (unavailableReason != null) Modifier.alpha(0.5f) else Modifier),
        )
        if (isPlaying) {
            EqualizerOverlay(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
        if (unavailableReason != null) {
            var showUnavailableDialog by remember { mutableStateOf(false) }
            if (showUnavailableDialog) {
                TrackUnavailableDialog(
                    trackTitle = track.title,
                    reason = unavailableReason,
                    onDismiss = { showUnavailableDialog = false },
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clickable { showUnavailableDialog = true },
            ) {
                FavoriteSourceBadge(
                    icon = if (unavailableReason == UnavailableReason.DELETED) Icons.Filled.Block else Icons.Filled.Lock,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (favoriteSource != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (favoriteSource == FavoriteSource.ONLINE || favoriteSource == FavoriteSource.BOTH) {
                    FavoriteSourceBadge(Icons.Filled.Cloud, MaterialTheme.colorScheme.primary)
                }
                if (favoriteSource == FavoriteSource.LOCAL || favoriteSource == FavoriteSource.BOTH) {
                    FavoriteSourceBadge(Icons.Filled.PhoneAndroid, MaterialTheme.colorScheme.secondary)
                }
            }
        }
        AnimatedVisibility(
            visible = selectionActive,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                scaleOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
            modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
        AnimatedVisibility(
            visible = burstHeart,
            enter = fadeIn(spring(stiffness = Spring.StiffnessHigh)) +
                scaleIn(
                    initialScale = 0.4f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                ),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun FavoriteSourceBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
    }
}

@Composable
private fun EqualizerOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "b1",
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 24f, targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(375, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "b2",
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 12f, targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(625, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "b3",
    )

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(bar3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
        }
    }
}
