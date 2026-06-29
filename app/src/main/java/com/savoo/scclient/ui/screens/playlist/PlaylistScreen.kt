package com.savoo.scclient.ui.screens.playlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
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
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.TrackRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isResolvingTracks: Boolean = false,
    val error: String? = null,
)

@UnstableApi
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val favoritesDao: FavoritesDao,
    val playerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState = _uiState.asStateFlow()

    fun loadPlaylist(playlistId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val playlist = repository.getPlaylist(playlistId)
                val rawTracks = playlist.tracks ?: emptyList()
                playlist to rawTracks
            }.onSuccess { (playlist, rawTracks) ->
                _uiState.value = PlaylistUiState(
                    playlist = playlist,
                    tracks = rawTracks,
                    isLoading = false,
                    isResolvingTracks = rawTracks.any { it.title.isBlank() },
                )
                if (rawTracks.any { it.title.isBlank() }) {
                    resolveTracks(rawTracks)
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun resolveTracks(rawTracks: List<Track>) {
        val resolved = mutableListOf<Track>()
        for (t in rawTracks) {
            val full = if (t.title.isBlank()) {
                runCatching { repository.getTrack(t.id) }.getOrNull() ?: t
            } else t
            resolved.add(full)
            _uiState.value = _uiState.value.copy(tracks = resolved.toList())
        }
        _uiState.value = _uiState.value.copy(isResolvingTracks = false)
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

    fun isPlaylistFavoriteFlow(playlistId: Long) = favoritesDao.isPlaylistFavorite(playlistId)

    fun togglePlaylistFavorite(playlist: Playlist) {
        viewModelScope.launch {
            if (favoritesDao.isPlaylistFavoriteSync(playlist.id)) {
                favoritesDao.removePlaylist(playlist.id)
            } else {
                favoritesDao.addPlaylist(
                    FavoritePlaylist(
                        playlistId = playlist.id,
                        title = playlist.title,
                        artworkUrl = playlist.artworkUrl,
                        trackCount = playlist.trackCount,
                        username = playlist.user.username,
                        permalinkUrl = playlist.permalinkUrl,
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
fun PlaylistScreen(
    playlistId: Long,
    viewModel: PlaylistViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()

    androidx.compose.runtime.LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.playlist?.title ?: "") },
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
            state.playlist != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item {
                    val isPlaylistFav by viewModel.isPlaylistFavoriteFlow(playlistId).collectAsState(initial = false)
                    PlaylistHeader(
                        playlist = state.playlist!!,
                        isFavorite = isPlaylistFav,
                        onPlayAll = viewModel::playAll,
                        onToggleFavorite = { viewModel.togglePlaylistFavorite(state.playlist!!) },
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
                if (state.isResolvingTracks) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    isFavorite: Boolean,
    onPlayAll: () -> Unit,
    onToggleFavorite: () -> Unit,
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
            model = playlist.artworkUrl?.replace("-large", "-t500x500"),
            contentDescription = playlist.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(20.dp))
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = playlist.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "by ${playlist.user.username}",
            style = MaterialTheme.typography.bodyLarge,
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
