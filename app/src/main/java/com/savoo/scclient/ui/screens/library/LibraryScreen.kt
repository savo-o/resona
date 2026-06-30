package com.savoo.scclient.ui.screens.library

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.player.PlayerController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onFavorites: () -> Unit = {},
    onPlaylists: () -> Unit = {},
    onSettings: () -> Unit = {},
    onImportExport: () -> Unit = {},
    onFavoriteArtists: () -> Unit = {},
    onFavoritePlaylists: () -> Unit = {},
    playerController: PlayerController? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val favCount by viewModel.favoritesCount.collectAsState()
    val artistsCount by viewModel.artistsCount.collectAsState()
    val playlistsCount by viewModel.playlistsCount.collectAsState()
    val recentTracks = viewModel.playerController.getRecentTracks()
    val playerState by viewModel.playerController.state.collectAsState()
    val recentRowState = rememberLazyListState()

    LaunchedEffect(playerState.currentTrack?.id) {
        recentRowState.animateScrollToItem(0)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.library_title)) },
            actions = {
                IconButton(onClick = onImportExport) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Import / Export")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            LibraryLargeCard(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.library_favorites),
                subtitle = stringResource(R.string.library_tracks_count, favCount),
                onClick = onFavorites,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LibrarySmallCard(
                    icon = Icons.Filled.Album,
                    title = stringResource(R.string.library_albums),
                    subtitle = stringResource(R.string.library_playlists_count, playlistsCount),
                    onClick = onFavoritePlaylists,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
                LibrarySmallCard(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.library_artists),
                    subtitle = stringResource(R.string.library_artists_count, artistsCount),
                    onClick = onFavoriteArtists,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }

            LibraryLargeCard(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.library_playlists),
                subtitle = stringResource(R.string.library_playlists_count, playlistsCount),
                onClick = onPlaylists,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            if (recentTracks.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.library_recently_played),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                LazyRow(
                    state = recentRowState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recentTracks.take(10), key = { it.id }) { track ->
                        val isCurrentTrack = playerState.currentTrack?.id == track.id
                        val isPlaying = playerState.isPlaying && isCurrentTrack
                        val isLoading = playerState.loadingTrackId == track.id && !isCurrentTrack

                        RecentlyPlayedItem(
                            track = track,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            onClick = {
                                val idx = recentTracks.indexOfFirst { t -> t.id == track.id }
                                if (isCurrentTrack) {
                                    viewModel.playerController.togglePlayPause()
                                } else {
                                    viewModel.playerController.playQueue(recentTracks, idx.coerceAtLeast(0))
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LibraryLargeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "largeCard",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f))
            }
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LibrarySmallCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "smallCard",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.weight(1f))
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun RecentlyPlayedItem(
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "recentScale",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "b1",
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 22f, targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(375, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "b2",
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(625, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "b3",
    )

    Column(
        modifier = Modifier
            .width(120.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = track.artworkUrl?.replace("-large", "-t500x500"),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
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
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.inverseSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = track.user.username,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun <T> spring(
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessMedium,
): androidx.compose.animation.core.AnimationSpec<T> = androidx.compose.animation.core.spring(
    dampingRatio = dampingRatio,
    stiffness = stiffness,
)
