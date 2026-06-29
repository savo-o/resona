package com.savoo.scclient.ui.screens.artist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.BadgeRepository
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.TrackRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistUiState(
    val user: User? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@UnstableApi
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val favoritesDao: FavoritesDao,
    val playerController: PlayerController,
    private val badgeRepository: BadgeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState = _uiState.asStateFlow()

    fun getBadges(userId: Long): StateFlow<List<String>> = badgeRepository.getBadges(userId)
    val developerMode = settingsRepository.settings.map { it.developerMode }

    fun loadArtist(userId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val user = repository.getUser(userId)
                val tracks = repository.getUserTracks(userId)
                Pair(user, tracks)
            }.onSuccess { (user, tracks) ->
                _uiState.value = ArtistUiState(user = user, tracks = tracks, isLoading = false)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun playAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks, 0)
        }
    }

    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        val idx = tracks.indexOfFirst { it.id == track.id }
        playerController.playQueue(tracks, idx.coerceAtLeast(0))
    }

    fun isFavoriteFlow(trackId: Long) = favoritesDao.isTrackFavorite(trackId)

    fun isArtistFavoriteFlow(artistId: Long) = favoritesDao.isArtistFavorite(artistId)

    fun toggleArtistFavorite(user: User) {
        viewModelScope.launch {
            if (favoritesDao.isArtistFavoriteSync(user.id)) {
                favoritesDao.removeArtist(user.id)
            } else {
                favoritesDao.addArtist(
                    FavoriteArtist(
                        artistId = user.id,
                        username = user.username,
                        fullName = user.fullName,
                        avatarUrl = user.avatarUrl,
                        followersCount = user.followersCount,
                        permalinkUrl = user.permalinkUrl,
                    )
                )
            }
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            if (favoritesDao.isTrackFavoriteSync(track.id)) {
                favoritesDao.removeTrack(track.id)
            } else {
                favoritesDao.addTrack(
                    FavoriteTrack(
                        trackId = track.id,
                        title = track.title,
                        username = track.user.username,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs,
                        permalinkUrl = track.permalinkUrl,
                        userId = track.user.id,
                        userAvatarUrl = track.user.avatarUrl,
                    )
                )
            }
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    userId: Long,
    viewModel: ArtistViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    var selectedBadge by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(userId) {
        viewModel.loadArtist(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.user?.fullName?.ifBlank { null } ?: state.user?.username ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
            }
            state.user != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item {
                    val isArtistFav by viewModel.isArtistFavoriteFlow(userId).collectAsState(initial = false)
                    val artistBadges by viewModel.getBadges(userId).collectAsState()
                    val isDeveloper by viewModel.developerMode.collectAsState(initial = false)
                    ArtistHeader(
                        user = state.user!!,
                        trackCount = state.tracks.size,
                        badges = artistBadges,
                        showId = isDeveloper,
                        isFavorite = isArtistFav,
                        onPlayAll = viewModel::playAll,
                        onToggleFavorite = { viewModel.toggleArtistFavorite(state.user!!) },
                        onBadgeClick = { selectedBadge = it },
                    )
                }
                item {
                    Text(
                        text = "Tracks",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(state.tracks, key = { it.id }) { track ->
                    val isFav by viewModel.isFavoriteFlow(track.id).collectAsState(initial = false)
                    TrackRow(
                        track = track,
                        onClick = { viewModel.playTrack(track) },
                        isFavorite = isFav,
                        isLoading = playerState.loadingTrackId == track.id,
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
            }
        }
    }

    selectedBadge?.let { badge ->
        com.savoo.scclient.ui.components.BadgeBottomSheet(
            badge = badge,
            profileName = state.user?.fullName?.ifBlank { null } ?: state.user?.username ?: "",
            onDismiss = { selectedBadge = null },
        )
    }
}
}

@Composable
private fun ArtistHeader(
    user: User,
    trackCount: Int,
    badges: List<String> = emptyList(),
    showId: Boolean = false,
    isFavorite: Boolean,
    onPlayAll: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBadgeClick: (String) -> Unit = {},
) {
    var buttonPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (buttonPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "playAll",
        finishedListener = { buttonPressed = false }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = user.avatarUrl?.replace("-large", "-t200x200"),
            contentDescription = user.username,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
        )

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = user.fullName?.ifBlank { null } ?: user.username,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            badges.forEach { badge ->
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = when (badge) {
                        "developer" -> Icons.Filled.Handyman
                        "supporter" -> Icons.Filled.Star
                        else -> Icons.Filled.Star
                    },
                    contentDescription = badge,
                    tint = when (badge) {
                        "developer" -> MaterialTheme.colorScheme.tertiary
                        "supporter" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onBadgeClick(badge) },
                )
            }
        }

        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showId) {
            Text(
                text = "ID: ${user.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        user.followersCount?.let {
            Text(
                text = "$it followers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text(
            text = "$trackCount tracks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = { buttonPressed = true; onPlayAll() },
                modifier = Modifier
                    .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play All", style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
