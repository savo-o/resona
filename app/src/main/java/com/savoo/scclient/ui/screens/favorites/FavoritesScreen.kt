package com.savoo.scclient.ui.screens.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.TrackRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesDao: FavoritesDao,
    val playerController: PlayerController,
    val offlineTrackManager: OfflineTrackManager,
) : ViewModel() {

    val tracks = favoritesDao.getAllTracks().map { list ->
        list.map { fav ->
            Track(
                id = fav.trackId,
                title = fav.title,
                durationMs = fav.durationMs,
                artworkUrl = fav.artworkUrl,
                user = User(id = fav.userId, username = fav.username, avatarUrl = fav.userAvatarUrl),
                permalinkUrl = fav.permalinkUrl,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playTrack(track: Track) {
        val currentTracks = tracks.value
        val idx = currentTracks.indexOfFirst { it.id == track.id }
        playerController.playQueue(currentTracks, idx.coerceAtLeast(0))
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            if (favoritesDao.isTrackFavoriteSync(trackId)) favoritesDao.removeTrack(trackId)
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val tracks by viewModel.tracks.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.user.username.contains(searchQuery, ignoreCase = true)
        }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.favorites_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (tracks.isNotEmpty()) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (filteredTracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (tracks.isEmpty()) stringResource(R.string.favorites_empty)
                        else stringResource(R.string.search_nothing_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val isCurrentTrack = playerState.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            onClick = { viewModel.playTrack(track) },
                            isLoading = playerState.loadingTrackId == track.id,
                            isFavorite = true,
                            isPlaying = playerState.isPlaying && isCurrentTrack,
                            onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                            onTogglePlayPause = {
                                if (isCurrentTrack) viewModel.playerController.togglePlayPause()
                                else viewModel.playTrack(track)
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
