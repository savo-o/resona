package com.savoo.scclient.ui.screens.playlist

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.releaseYear
import com.savoo.scclient.data.model.restrictionReason
import com.savoo.scclient.data.repository.ArtworkGlowSource
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.CollapsingDetailTopBar
import com.savoo.scclient.ui.components.DetailActionRow
import com.savoo.scclient.ui.components.DetailCardCarousel
import com.savoo.scclient.ui.components.DetailCardItem
import com.savoo.scclient.ui.components.DetailSectionTitle
import com.savoo.scclient.ui.components.LocalArtworkGlowSource
import com.savoo.scclient.ui.components.TrackArtwork
import com.savoo.scclient.ui.components.TrackRow
import com.savoo.scclient.ui.components.TrackSelectionBar
import com.savoo.scclient.ui.components.TrackSort
import com.savoo.scclient.ui.components.TrackSortButton
import com.savoo.scclient.ui.components.applySortOption
import com.savoo.scclient.ui.components.formatTotalDuration
import com.savoo.scclient.ui.components.hiResArtwork
import com.savoo.scclient.ui.components.rememberArtworkColor
import com.savoo.scclient.ui.components.rememberCollapseProgress
import com.savoo.scclient.ui.components.rememberTrackSelection
import com.savoo.scclient.ui.haptics.rememberHapticTick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TRACK_RESOLVE_CONCURRENCY = 8

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isResolvingTracks: Boolean = false,
    val moreByOwner: List<Playlist> = emptyList(),
    val error: String? = null,
)

@UnstableApi
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val favoritesDao: FavoritesDao,
    private val favoritesRepository: com.savoo.scclient.data.repository.FavoritesRepository,
    private val offlineTrackManager: OfflineTrackManager,
    val playerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState = _uiState.asStateFlow()

    val downloadingTrackIds = offlineTrackManager.downloadingTrackIds

    private var loadedPlaylistId: Long? = null

    fun loadPlaylist(playlistId: Long) {
        if (loadedPlaylistId == playlistId && _uiState.value.playlist != null) return
        loadedPlaylistId = playlistId
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
                launch {
                    val ownerId = playlist.user.id
                    val more = runCatching {
                        repository.getUserAlbums(ownerId).ifEmpty { repository.getUserPlaylists(ownerId) }
                    }.getOrDefault(emptyList()).filter { it.id != playlist.id }
                    _uiState.update { it.copy(moreByOwner = more) }
                }
                if (rawTracks.any { it.title.isBlank() }) {
                    resolveTracks(rawTracks)
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun resolveTracks(rawTracks: List<Track>) {
        val resolved = rawTracks.toMutableList()
        val pending = rawTracks.withIndex().filter { it.value.title.isBlank() }
        pending.chunked(TRACK_RESOLVE_CONCURRENCY).forEach { chunk ->
            coroutineScope {
                chunk.map { (index, t) ->
                    async {
                        val full = runCatching { repository.getTrack(t.id) }.getOrNull()
                        if (full != null) resolved[index] = full
                    }
                }.awaitAll()
            }
            _uiState.value = _uiState.value.copy(tracks = resolved.toList())
        }
        _uiState.value = _uiState.value.copy(isResolvingTracks = false)
    }

    fun playAll(tracks: List<Track> = _uiState.value.tracks) {
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks, 0, tag = playlistQueueTag(), startExact = false)
        }
    }

    fun playTrack(track: Track, tracks: List<Track> = _uiState.value.tracks) {
        val idx = tracks.indexOfFirst { it.id == track.id }
        playerController.playQueue(tracks, idx.coerceAtLeast(0), tag = playlistQueueTag())
    }

    fun shuffleAll(tracks: List<Track> = _uiState.value.tracks) {
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks.shuffled(), 0, tag = playlistQueueTag(), startExact = false)
        }
    }

    fun isPlayingFromPlaylist(queueTag: String?): Boolean = queueTag != null && queueTag == playlistQueueTag()

    private fun playlistQueueTag(): String? = _uiState.value.playlist?.title?.let { "playlist:$it" }

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
fun PlaylistScreen(
    playlistId: Long,
    viewModel: PlaylistViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    val unavailableReasons by viewModel.playerController.unavailableReasons.collectAsState()
    val downloadingIds by viewModel.downloadingTrackIds.collectAsState()
    var sort by remember { mutableStateOf(TrackSort()) }
    val listState = rememberLazyListState()
    val selection = rememberTrackSelection()

    LaunchedEffect(playlistId) { viewModel.loadPlaylist(playlistId) }

    val context = LocalContext.current
    val sortedTracks = remember(state.tracks, sort) { state.tracks.applySortOption(sort) }
    val collapse = rememberCollapseProgress(listState, 300.dp)
    val barBackground = rememberCollapseProgress(listState, 40.dp)
    val playlist = state.playlist
    val artwork = playlist?.artworkUrl ?: state.tracks.firstOrNull { it.artworkUrl != null }?.artworkUrl
    val glowSource = LocalArtworkGlowSource.current
    val artworkColor = if (glowSource == ArtworkGlowSource.ARTWORK) rememberArtworkColor(artwork) else null
    val themeTint = MaterialTheme.colorScheme.primary
    val tint by animateColorAsState(
        targetValue = if (glowSource == ArtworkGlowSource.OFF) MaterialTheme.colorScheme.surfaceVariant else artworkColor ?: themeTint,
        animationSpec = tween(700),
        label = "playlistTint",
    )
    val isPlayingThis = playerState.isPlaying && viewModel.isPlayingFromPlaylist(playerState.queueTag)
    val share: (() -> Unit)? = playlist?.permalinkUrl?.let { url ->
        {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.artist_error, state.error ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                playlist != null -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    item(key = "header") {
                        PlaylistHeader(
                            playlist = playlist,
                            artworkUrl = artwork,
                            tint = tint,
                            trackCount = state.tracks.size.takeIf { it > 0 } ?: playlist.trackCount,
                            totalDurationMs = playlist.durationMs?.takeIf { it > 0 } ?: state.tracks.sumOf { it.durationMs },
                            scrollOffsetPx = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0,
                            glowEnabled = glowSource != ArtworkGlowSource.OFF,
                            onOwnerClick = { onArtistClick(playlist.user.id) },
                        )
                    }
                    item(key = "actions") {
                        val isPlaylistFav by viewModel.isPlaylistFavoriteFlow(playlistId).collectAsState(initial = false)
                        DetailActionRow(
                            isFavorite = isPlaylistFav,
                            onToggleFavorite = { viewModel.togglePlaylistFavorite(playlist) },
                            isPlayingThis = isPlayingThis,
                            playEnabled = sortedTracks.isNotEmpty(),
                            onPlay = {
                                if (viewModel.isPlayingFromPlaylist(playerState.queueTag)) viewModel.playerController.togglePlayPause()
                                else viewModel.playAll(sortedTracks)
                            },
                            onShuffle = { viewModel.shuffleAll(sortedTracks) },
                            onShare = share,
                            extraActions = {
                                if (state.tracks.isNotEmpty()) {
                                    TrackSortButton(sort = sort, onSortChange = { sort = it })
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                    if (state.isResolvingTracks) {
                        item(key = "resolving") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    playlist.releaseDate?.let { date ->
                        item(key = "release") {
                            Text(
                                formatReleaseDate(date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            )
                        }
                    }
                    if (state.moreByOwner.isNotEmpty()) {
                        item(key = "more_by") {
                            Column {
                                DetailSectionTitle(stringResource(R.string.detail_more_by, playlist.user.username))
                                DetailCardCarousel(
                                    items = state.moreByOwner.map { other ->
                                        DetailCardItem(
                                            id = other.id,
                                            title = other.title,
                                            subtitle = other.releaseYear,
                                            artworkUrl = other.artworkUrl ?: other.tracks?.firstOrNull { it.artworkUrl != null }?.artworkUrl,
                                        )
                                    },
                                    onClick = onPlaylistClick,
                                )
                            }
                        }
                    }
                }
            }

            CollapsingDetailTopBar(
                title = playlist?.title.orEmpty(),
                progress = collapse,
                backgroundProgress = barBackground,
                containerColor = lerp(
                    lerp(MaterialTheme.colorScheme.surface, tint, 0.4f),
                    MaterialTheme.colorScheme.surfaceContainer,
                    collapse,
                ),
                onBack = onBack,
            )
        }
    }
}

private fun formatReleaseDate(raw: String): String = runCatching {
    val date = java.time.OffsetDateTime.parse(raw).toLocalDate()
    date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))
}.getOrElse {
    runCatching {
        java.time.LocalDate.parse(raw.take(10))
            .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))
    }.getOrDefault(raw.take(10))
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    artworkUrl: String?,
    tint: Color,
    trackCount: Int,
    totalDurationMs: Long,
    scrollOffsetPx: Int,
    glowEnabled: Boolean,
    onOwnerClick: () -> Unit,
) {
    val haptic = rememberHapticTick()
    val surface = MaterialTheme.colorScheme.surface
    var descriptionExpanded by remember { mutableStateOf(false) }
    val kind = stringResource(
        when (playlist.setType?.lowercase()) {
            "album" -> R.string.playlist_kind_album
            "ep" -> R.string.playlist_kind_ep
            "single" -> R.string.playlist_kind_single
            "compilation" -> R.string.playlist_kind_compilation
            else -> if (playlist.isAlbum == true) R.string.playlist_kind_album else R.string.playlist_kind_playlist
        }
    )
    val meta = listOfNotNull(
        kind,
        playlist.releaseYear,
        pluralStringResource(R.plurals.detail_tracks_count, trackCount, trackCount),
        totalDurationMs.takeIf { it > 0 }?.let { formatTotalDuration(it) },
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = 0.4f),
                    0.6f to tint.copy(alpha = 0.18f),
                    1f to surface,
                )
            )
            .statusBarsPadding()
            .padding(top = 64.dp, start = 20.dp, end = 20.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TrackArtwork(
                artworkUrl = artworkUrl,
                contentDescription = playlist.title,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        val fraction = (scrollOffsetPx / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val scale = 1f - fraction * 0.3f
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - fraction
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        shadowElevation = if (glowEnabled) 24.dp.toPx() else 0f
                        shape = RoundedCornerShape(28.dp)
                        clip = false
                        ambientShadowColor = tint
                        spotShadowColor = tint
                    },
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            playlist.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        playlist.description?.trim()?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .animateContentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { descriptionExpanded = !descriptionExpanded },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { haptic(); onOwnerClick() }
                .padding(end = 10.dp, top = 2.dp, bottom = 2.dp),
        ) {
            AsyncImage(
                model = hiResArtwork(playlist.user.avatarUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                playlist.user.fullName?.ifBlank { null } ?: playlist.user.username,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
