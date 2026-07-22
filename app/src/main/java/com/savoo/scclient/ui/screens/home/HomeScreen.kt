package com.savoo.scclient.ui.screens.home

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.ui.components.TrackArtwork
import kotlinx.coroutines.delay

private val BlobShape = RoundedCornerShape(
    topStartPercent = 52,
    topEndPercent = 18,
    bottomStartPercent = 18,
    bottomEndPercent = 52,
)

@Composable
fun HomeScreen(
    onAccount: () -> Unit = {},
    onSettings: () -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onFavorites: () -> Unit = {},
    onOfflineTracks: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsState()
    val favoriteTracks by viewModel.favoriteTracks.collectAsState()
    val offlineTracks by viewModel.offlineTracks.collectAsState()
    val favoriteArtists by viewModel.favoriteArtists.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()

    var recentTracks by remember { mutableStateOf(viewModel.playerController.getRecentTracks()) }
    LaunchedEffect(playerState.currentTrack?.id) {
        recentTracks = viewModel.playerController.getRecentTracks()
    }

    val mixTracks = remember(recentTracks, favoriteTracks, offlineTracks) {
        val pool = (recentTracks + favoriteTracks + offlineTracks).distinctBy { it.id }
        if (pool.isEmpty()) emptyList() else pool.shuffled()
    }

    var titleVisible by remember { mutableStateOf(false) }
    var heroVisible by remember { mutableStateOf(false) }
    var sectionsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        titleVisible = true
        delay(120)
        heroVisible = true
        delay(150)
        sectionsVisible = true
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            HomeTopBar(
                avatarUrl = user?.avatarUrl,
                onAccount = onAccount,
                onSettings = onSettings,
            )

            HomeHero(
                visible = titleVisible,
                heroVisible = heroVisible,
                mixTracks = mixTracks,
                isMixPlaying = playerState.isPlaying && mixTracks.any { it.id == playerState.currentTrack?.id },
                isMixLoading = playerState.loadingTrackId != null && mixTracks.any { it.id == playerState.loadingTrackId },
                onPlayToggle = {
                    val currentInMix = mixTracks.any { it.id == playerState.currentTrack?.id }
                    if (currentInMix) {
                        viewModel.playerController.togglePlayPause()
                    } else {
                        viewModel.playMix(mixTracks)
                    }
                },
            )

            Spacer(Modifier.height(32.dp))

            AnimatedSections(visible = sectionsVisible) {
                if (recentTracks.isNotEmpty()) {
                    TrackSection(
                        title = stringResource(R.string.home_section_jump_back_in),
                        tracks = recentTracks.take(10),
                        playerState = playerState,
                        onTrackClick = { track ->
                            if (playerState.currentTrack?.id == track.id) {
                                viewModel.playerController.togglePlayPause()
                            } else {
                                viewModel.playFrom(recentTracks, track.id)
                            }
                        },
                    )
                }

                if (favoriteTracks.isNotEmpty()) {
                    TrackSection(
                        title = stringResource(R.string.home_section_favorites),
                        tracks = favoriteTracks.take(10),
                        playerState = playerState,
                        onSeeAll = onFavorites,
                        onTrackClick = { track ->
                            if (playerState.currentTrack?.id == track.id) {
                                viewModel.playerController.togglePlayPause()
                            } else {
                                viewModel.playFrom(favoriteTracks, track.id)
                            }
                        },
                    )
                }

                if (offlineTracks.isNotEmpty()) {
                    TrackSection(
                        title = stringResource(R.string.home_section_offline),
                        tracks = offlineTracks.take(10),
                        playerState = playerState,
                        onSeeAll = onOfflineTracks,
                        onTrackClick = { track ->
                            if (playerState.currentTrack?.id == track.id) {
                                viewModel.playerController.togglePlayPause()
                            } else {
                                viewModel.playFrom(offlineTracks, track.id)
                            }
                        },
                    )
                }

                if (favoriteArtists.isNotEmpty()) {
                    ArtistSection(
                        title = stringResource(R.string.home_section_artists),
                        artists = favoriteArtists.take(10),
                        onArtistClick = onArtistClick,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnimatedSections(visible: Boolean, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "sectionsAlpha",
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 32.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "sectionsOffset",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = offsetY.toPx(); this.alpha = alpha },
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        content()
    }
}

@Composable
private fun HomeTopBar(
    avatarUrl: String?,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onAccount),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl.replace("-large", "-t500x500"),
                    contentDescription = stringResource(R.string.nav_account),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.Headphones,
                    contentDescription = stringResource(R.string.nav_account),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeHero(
    visible: Boolean,
    heroVisible: Boolean,
    mixTracks: List<Track>,
    isMixPlaying: Boolean,
    isMixLoading: Boolean,
    onPlayToggle: () -> Unit,
) {
    val titleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "titleAlpha",
    )
    val titleOffsetX by animateDpAsState(
        targetValue = if (visible) 0.dp else (-32).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "titleOffsetX",
    )
    val playButtonScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "playButtonScale",
    )

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { translationX = titleOffsetX.toPx(); alpha = titleAlpha },
            ) {
                Text(
                    text = stringResource(R.string.home_mix_line1),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_mix_line2),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                val artistNames = remember(mixTracks) {
                    mixTracks.map { it.user.username }.filter { it.isNotBlank() }.distinct().take(3)
                }
                Text(
                    text = if (artistNames.isNotEmpty()) {
                        artistNames.joinToString(", ")
                    } else {
                        stringResource(R.string.home_mix_subtitle_count, mixTracks.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (mixTracks.isNotEmpty()) {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val pressScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.9f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    label = "heroPlayPress",
                )
                Surface(
                    onClick = onPlayToggle,
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .size(72.dp)
                        .scale(playButtonScale * pressScale),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isMixLoading -> LoadingIndicator(modifier = Modifier.size(28.dp))
                            isMixPlaying -> Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.player_play), modifier = Modifier.size(30.dp))
                            else -> Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.player_play), modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (mixTracks.isNotEmpty()) {
            MixBlob(visible = heroVisible, mixTracks = mixTracks, onClick = onPlayToggle)
        } else {
            EmptyMixCard(visible = heroVisible)
        }
    }
}

@Composable
private fun MixBlob(visible: Boolean, mixTracks: List<Track>, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "blobScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "blobAlpha",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "blobFloat")
    val bob1 by infiniteTransition.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob1",
    )
    val bob2 by infiniteTransition.animateFloat(
        initialValue = 5f, targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob2",
    )

    val mainTrack = mixTracks.getOrNull(0)
    val cornerTrack1 = mixTracks.getOrNull(1)
    val cornerTrack2 = mixTracks.getOrNull(2)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clickable(onClick = onClick),
    ) {
        if (cornerTrack2 != null) {
            TrackArtwork(
                artworkUrl = cornerTrack2.artworkUrl,
                contentDescription = cornerTrack2.title,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .graphicsLayer { translationY = bob2 },
            )
        }
        if (cornerTrack1 != null) {
            TrackArtwork(
                artworkUrl = cornerTrack1.artworkUrl,
                contentDescription = cornerTrack1.title,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-8).dp, y = (-4).dp)
                    .graphicsLayer { translationY = bob1 },
            )
        }
        TrackArtwork(
            artworkUrl = mainTrack?.artworkUrl,
            contentDescription = mainTrack?.title,
            shape = BlobShape,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.92f)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale; this.alpha = alpha
                },
        )
    }
}

@Composable
private fun EmptyMixCard(visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "emptyAlpha",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .graphicsLayer { this.alpha = alpha },
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Headphones, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.home_mix_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.home_mix_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TrackSection(
    title: String,
    tracks: List<Track>,
    playerState: com.savoo.scclient.player.PlaybackState,
    onTrackClick: (Track) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    Column {
        SectionHeader(title = title, onSeeAll = onSeeAll)
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks, key = { it.id }) { track ->
                val isCurrentTrack = playerState.currentTrack?.id == track.id
                HomeTrackCard(
                    track = track,
                    isPlaying = playerState.isPlaying && isCurrentTrack,
                    isLoading = playerState.loadingTrackId == track.id && !isCurrentTrack,
                    onClick = { onTrackClick(track) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            IconButton(onClick = onSeeAll, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeTrackCard(
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
        label = "homeTrackScale",
    )

    Column(
        modifier = Modifier
            .width(120.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
    ) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            TrackArtwork(
                artworkUrl = track.artworkUrl,
                contentDescription = track.title,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.size(120.dp),
            )
            if (isPlaying || isLoading) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        LoadingIndicator(modifier = Modifier.size(26.dp), color = MaterialTheme.colorScheme.inverseSurface)
                    } else {
                        Icon(Icons.Filled.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = track.user.username,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArtistSection(
    title: String,
    artists: List<FavoriteArtist>,
    onArtistClick: (Long) -> Unit,
) {
    Column {
        SectionHeader(title = title, onSeeAll = null)
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(artists, key = { it.artistId }) { artist ->
                HomeArtistChip(artist = artist, onClick = { onArtistClick(artist.artistId) })
            }
        }
    }
}

@Composable
private fun HomeArtistChip(artist: FavoriteArtist, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "artistChipScale",
    )
    Column(
        modifier = Modifier
            .width(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (artist.avatarUrl != null) {
                AsyncImage(
                    model = artist.avatarUrl.replace("-large", "-t500x500"),
                    contentDescription = artist.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.Headphones,
                    contentDescription = artist.username,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.username,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
