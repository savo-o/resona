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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.R
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.model.restrictionReason
import com.savoo.scclient.data.remote.BadgeRepository
import com.savoo.scclient.data.repository.DrmTrackHiding
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.TrackRow
import com.savoo.scclient.ui.components.TrackSelectionBar
import com.savoo.scclient.ui.components.TrackSort
import com.savoo.scclient.ui.components.TrackSortButton
import com.savoo.scclient.ui.components.applySortOption
import com.savoo.scclient.ui.components.rememberTrackSelection
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

private fun List<Track>.applyDrmHiding(mode: DrmTrackHiding): List<Track> = when (mode) {
    DrmTrackHiding.OFF -> this
    DrmTrackHiding.FULL -> filter { it.restrictionReason() == null }
    DrmTrackHiding.PARTIAL -> {
        val playableTitles = filter { it.restrictionReason() == null }
            .mapTo(mutableSetOf()) { it.title.trim().lowercase() }
        filter { it.restrictionReason() == null || it.title.trim().lowercase() !in playableTitles }
    }
}

@UnstableApi
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val favoritesDao: FavoritesDao,
    private val favoritesRepository: com.savoo.scclient.data.repository.FavoritesRepository,
    private val offlineTrackManager: OfflineTrackManager,
    val playerController: PlayerController,
    private val badgeRepository: BadgeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState = _uiState.asStateFlow()

    val downloadingTrackIds = offlineTrackManager.downloadingTrackIds

    fun getBadges(userId: Long): StateFlow<List<String>> = badgeRepository.getBadges(userId)
    val developerMode = settingsRepository.settings.map { it.developerMode }
    val drmTrackHiding = settingsRepository.settings.map { it.drmTrackHiding }

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

    fun playAll(tracks: List<Track> = _uiState.value.tracks) {
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks, 0, tag = artistQueueTag(), startExact = false)
        }
    }

    fun playTrack(track: Track, tracks: List<Track> = _uiState.value.tracks) {
        val idx = tracks.indexOfFirst { it.id == track.id }
        playerController.playQueue(tracks, idx.coerceAtLeast(0), tag = artistQueueTag())
    }

    private fun artistQueueTag(): String? = _uiState.value.user?.username?.let { "artist:$it" }

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
            favoritesRepository.toggleTrackFavorite(track)
        }
    }

    fun isOfflineFlow(trackId: Long) = offlineTrackManager.isOfflineTrack(trackId)

    fun saveForOffline(track: Track) {
        viewModelScope.launch { offlineTrackManager.saveForOffline(track) }
    }

    fun removeFromOffline(trackId: Long) {
        viewModelScope.launch { offlineTrackManager.removeFromOffline(trackId) }
    }

    fun toggleDownload(track: Track) {
        viewModelScope.launch {
            if (offlineTrackManager.isOfflineTrackSync(track.id)) {
                offlineTrackManager.removeFromOffline(track.id)
            } else {
                offlineTrackManager.saveForOffline(track)
            }
        }
    }

    fun toggleFavoriteForSelected(ids: Set<Long>) {
        viewModelScope.launch {
            _uiState.value.tracks.filter { it.id in ids }.forEach { favoritesRepository.toggleTrackFavorite(it) }
        }
    }

    fun toggleDownloadForSelected(ids: Set<Long>) {
        viewModelScope.launch {
            val selectedTracks = _uiState.value.tracks.filter { it.id in ids }
            val currentlyOffline = selectedTracks.filter { offlineTrackManager.isOfflineTrackSync(it.id) }.map { it.id }.toSet()
            val allOffline = ids.isNotEmpty() && currentlyOffline.size == ids.size
            if (allOffline) {
                ids.forEach { offlineTrackManager.removeFromOffline(it) }
            } else {
                offlineTrackManager.enqueueDownloads(selectedTracks.filter { it.id !in currentlyOffline })
            }
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistScreen(
    userId: Long,
    viewModel: ArtistViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    val unavailableReasons by viewModel.playerController.unavailableReasons.collectAsState()
    val downloadingIds by viewModel.downloadingTrackIds.collectAsState()
    var selectedBadge by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(TrackSort()) }
    val listState = rememberLazyListState()
    val selection = rememberTrackSelection()

    androidx.compose.runtime.LaunchedEffect(userId) {
        viewModel.loadArtist(userId)
    }
    androidx.compose.runtime.LaunchedEffect(sort) { listState.animateScrollToItem(0) }

    val context = LocalContext.current
    val drmHiding by viewModel.drmTrackHiding.collectAsState(initial = DrmTrackHiding.FULL)
    val visibleTracks = state.tracks.applyDrmHiding(drmHiding)
    val sortedTracks = visibleTracks.applySortOption(sort)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.user?.fullName?.ifBlank { null } ?: state.user?.username ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (visibleTracks.isNotEmpty()) {
                        TrackSortButton(sort = sort, onSortChange = { sort = it })
                    }
                    state.user?.permalinkUrl?.let { url ->
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
        bottomBar = {
            TrackSelectionBar(
                selectedCount = selection.count,
                onClear = { selection.clear() },
                onQueueAll = {
                    viewModel.playerController.addToQueue(sortedTracks.filter { it.id in selection.selectedIds })
                    selection.clear()
                },
                onSelectAll = { selection.selectAll(sortedTracks.map { it.id }) },
                onFavoriteAll = {
                    viewModel.toggleFavoriteForSelected(selection.selectedIds)
                    selection.clear()
                },
                onDownloadAll = {
                    viewModel.toggleDownloadForSelected(selection.selectedIds)
                    selection.clear()
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.artist_error, state.error ?: ""),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.user != null -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item {
                    val isArtistFav by viewModel.isArtistFavoriteFlow(userId).collectAsState(initial = false)
                    val artistBadges by viewModel.getBadges(userId).collectAsState()
                    val isDeveloper by viewModel.developerMode.collectAsState(initial = false)
                    ArtistHeader(
                        user = state.user!!,
                        trackCount = visibleTracks.size,
                        badges = artistBadges,
                        showId = isDeveloper,
                        isFavorite = isArtistFav,
                        onPlayAll = { viewModel.playAll(sortedTracks) },
                        onToggleFavorite = { viewModel.toggleArtistFavorite(state.user!!) },
                        onBadgeClick = { selectedBadge = it },
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.artist_tracks),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(sortedTracks, key = { it.id }) { track ->
                    val isFav by viewModel.isFavoriteFlow(track.id).collectAsState(initial = false)
                    val isOffline by viewModel.isOfflineFlow(track.id).collectAsState(initial = false)
                    val isCurrentTrack = playerState.currentTrack?.id == track.id
                    TrackRow(
                        track = track,
                        onClick = { viewModel.playTrack(track, sortedTracks) },
                        isFavorite = isFav,
                        isLoading = playerState.loadingTrackId == track.id,
                        isPlaying = playerState.isPlaying && isCurrentTrack,
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        onTogglePlayPause = {
                            if (isCurrentTrack) viewModel.playerController.togglePlayPause()
                            else viewModel.playTrack(track, sortedTracks)
                        },
                        isDownloaded = isOffline,
                        isDownloading = track.id in downloadingIds,
                            onToggleDownload = { viewModel.toggleDownload(track) },
                        selectionActive = selection.isActive,
                        isSelected = selection.contains(track.id),
                        onLongPress = { selection.toggle(track.id) },
                        unavailableReason = unavailableReasons[track.id] ?: track.restrictionReason(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                    )
            }
        }
    }

    selectedBadge?.let { badge ->
        com.savoo.scclient.ui.components.BadgeBottomSheet(
            badge = badge,
            profileName = state.user?.fullName?.ifBlank { null } ?: state.user?.username ?: "",
            onDismiss = { selectedBadge = null },
            onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
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
            model = user.avatarUrl?.replace("-large", "-t500x500"),
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
            text = stringResource(R.string.artist_at_username, user.username),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showId) {
            Text(
                text = stringResource(R.string.artist_id, user.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        user.followersCount?.let {
            Text(
                text = stringResource(R.string.artist_followers, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text(
            text = stringResource(R.string.artist_tracks_count, trackCount),
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
                    Text(stringResource(R.string.play_all), style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
