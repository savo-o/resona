package com.savoo.scclient.ui.screens.offline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import com.savoo.scclient.data.model.OfflineTrack
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
class OfflineTracksViewModel @Inject constructor(
    private val offlineTrackManager: OfflineTrackManager,
    val playerController: PlayerController,
) : ViewModel() {

    val tracks = offlineTrackManager.getAllOfflineTracks().map { list ->
        list.map { offline ->
            Track(
                id = offline.trackId,
                title = offline.title,
                durationMs = offline.durationMs,
                artworkUrl = offline.artworkUrl,
                user = User(id = offline.userId, username = offline.username, avatarUrl = offline.userAvatarUrl),
                permalinkUrl = offline.permalinkUrl,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playTrack(track: Track) {
        val currentTracks = tracks.value
        val idx = currentTracks.indexOfFirst { it.id == track.id }
        playerController.playQueue(currentTracks, idx.coerceAtLeast(0))
    }

    fun removeFromOffline(trackId: Long) {
        viewModelScope.launch {
            offlineTrackManager.removeFromOffline(trackId)
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineTracksScreen(
    viewModel: OfflineTracksViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val tracks by viewModel.tracks.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.library_offline)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )
    }) { padding ->
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.offline_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(tracks, key = { it.id }) { track ->
                    val isCurrentTrack = playerState.currentTrack?.id == track.id
                    TrackRow(
                        track = track,
                        onClick = { viewModel.playTrack(track) },
                        isLoading = playerState.loadingTrackId == track.id,
                        isPlaying = playerState.isPlaying && isCurrentTrack,
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
