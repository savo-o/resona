package com.savoo.scclient.ui.screens.artist

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.model.releaseYear
import com.savoo.scclient.data.model.restrictionReason
import com.savoo.scclient.data.remote.BadgeRepository
import com.savoo.scclient.data.repository.DrmTrackHiding
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.CollapsingDetailTopBar
import com.savoo.scclient.ui.components.DetailActionRow
import com.savoo.scclient.ui.components.DetailCardCarousel
import com.savoo.scclient.ui.components.DetailCardItem
import com.savoo.scclient.ui.components.DetailSectionTitle
import com.savoo.scclient.ui.components.TrackRow
import com.savoo.scclient.ui.components.TrackSelectionBar
import com.savoo.scclient.ui.components.TrackSort
import com.savoo.scclient.ui.components.TrackSortButton
import com.savoo.scclient.ui.components.applySortOption
import com.savoo.scclient.ui.components.badgeTitle
import com.savoo.scclient.ui.components.followersCountText
import com.savoo.scclient.ui.components.hiResArtwork
import com.savoo.scclient.ui.components.rememberCollapseProgress
import com.savoo.scclient.ui.components.rememberTrackSelection
import com.savoo.scclient.ui.haptics.rememberHapticTick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistUiState(
    val user: User? = null,
    val tracks: List<Track> = emptyList(),
    val topTracks: List<Track> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val relatedArtists: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingTracks: Boolean = false,
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

    private var loadedUserId: Long? = null

    fun loadArtist(userId: Long) {
        if (loadedUserId == userId && _uiState.value.user != null) return
        loadedUserId = userId
        viewModelScope.launch {
            _uiState.value = ArtistUiState(isLoading = true)
            val user = runCatching { repository.getUser(userId) }.getOrElse { e ->
                _uiState.value = ArtistUiState(error = e.message)
                return@launch
            }
            _uiState.update { it.copy(user = user, isLoading = false, isLoadingTracks = true) }
            launch {
                val tracks = runCatching { repository.getUserTracks(userId) }.getOrDefault(emptyList())
                _uiState.update { it.copy(tracks = tracks, isLoadingTracks = false) }
            }
            launch {
                val top = runCatching { repository.getUserTopTracks(userId) }.getOrDefault(emptyList())
                _uiState.update { it.copy(topTracks = top) }
            }
            launch {
                val albums = runCatching { repository.getUserAlbums(userId) }.getOrDefault(emptyList())
                _uiState.update { it.copy(albums = albums) }
            }
            launch {
                val playlists = runCatching { repository.getUserPlaylists(userId) }.getOrDefault(emptyList())
                _uiState.update { it.copy(playlists = playlists) }
            }
            launch {
                val related = runCatching { repository.getRelatedArtists(userId) }.getOrDefault(emptyList())
                _uiState.update { it.copy(relatedArtists = related) }
            }
        }
    }

    fun shuffleAll(tracks: List<Track> = _uiState.value.tracks) {
        if (tracks.isNotEmpty()) {
            playerController.playQueue(tracks.shuffled(), 0, tag = artistQueueTag(), startExact = false)
        }
    }

    fun isPlayingFromArtist(queueTag: String?): Boolean = queueTag != null && queueTag == artistQueueTag()

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
    onArtistClick: (Long) -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    val unavailableReasons by viewModel.playerController.unavailableReasons.collectAsState()
    val downloadingIds by viewModel.downloadingTrackIds.collectAsState()
    var selectedBadge by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(TrackSort()) }
    var popularExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val selection = rememberTrackSelection()
    val haptic = rememberHapticTick()

    LaunchedEffect(userId) { viewModel.loadArtist(userId) }

    val context = LocalContext.current
    val drmHiding by viewModel.drmTrackHiding.collectAsState(initial = DrmTrackHiding.FULL)
    val visibleTracks = remember(state.tracks, drmHiding) { state.tracks.applyDrmHiding(drmHiding) }
    val sortedTracks = remember(visibleTracks, sort) { visibleTracks.applySortOption(sort) }
    val popularTracks = remember(state.topTracks, visibleTracks, drmHiding) {
        val source = state.topTracks.ifEmpty { visibleTracks.sortedByDescending { it.playbackCount ?: 0L } }
        source.applyDrmHiding(drmHiding).filter { it.restrictionReason() == null }.take(10)
    }
    val collapse = rememberCollapseProgress(listState, 260.dp)
    val displayName = state.user?.fullName?.ifBlank { null } ?: state.user?.username ?: ""
    val isPlayingThis = playerState.isPlaying && viewModel.isPlayingFromArtist(playerState.queueTag)
    val share: (() -> Unit)? = state.user?.permalinkUrl?.let { url ->
        {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
    }

    @Composable
    fun ArtistTrack(track: Track, queue: List<Track>, rank: Int?, modifier: Modifier) {
        val isFav by viewModel.isFavoriteFlow(track.id).collectAsState(initial = false)
        val isOffline by viewModel.isOfflineFlow(track.id).collectAsState(initial = false)
        val isCurrentTrack = playerState.currentTrack?.id == track.id
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            if (rank != null) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
            }
            TrackRow(
                track = track,
                onClick = { viewModel.playTrack(track, queue) },
                isFavorite = isFav,
                isLoading = playerState.loadingTrackId == track.id,
                isPlaying = playerState.isPlaying && isCurrentTrack,
                onToggleFavorite = { viewModel.toggleFavorite(track) },
                onTogglePlayPause = {
                    if (isCurrentTrack) viewModel.playerController.togglePlayPause()
                    else viewModel.playTrack(track, queue)
                },
                isDownloaded = isOffline,
                isDownloading = track.id in downloadingIds,
                onToggleDownload = { viewModel.toggleDownload(track) },
                selectionActive = selection.isActive,
                isSelected = selection.contains(track.id),
                onLongPress = { selection.toggle(track.id) },
                unavailableReason = unavailableReasons[track.id] ?: track.restrictionReason(),
                modifier = Modifier.weight(1f),
            )
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
                state.user != null -> {
                    val user = state.user!!
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp),
                    ) {
                        item(key = "hero") {
                            val artistBadges by viewModel.getBadges(userId).collectAsState()
                            val isDeveloper by viewModel.developerMode.collectAsState(initial = false)
                            ArtistHero(
                                user = user,
                                badges = artistBadges,
                                showId = isDeveloper,
                                scrollOffsetPx = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0,
                                onBadgeClick = { selectedBadge = it },
                            )
                        }
                        item(key = "meta") {
                            ArtistMeta(user = user, trackCount = user.trackCount ?: visibleTracks.size)
                        }
                        item(key = "actions") {
                            val isArtistFav by viewModel.isArtistFavoriteFlow(userId).collectAsState(initial = false)
                            DetailActionRow(
                                isFavorite = isArtistFav,
                                onToggleFavorite = { viewModel.toggleArtistFavorite(user) },
                                isPlayingThis = isPlayingThis,
                                playEnabled = sortedTracks.isNotEmpty() || popularTracks.isNotEmpty(),
                                onPlay = {
                                    if (viewModel.isPlayingFromArtist(playerState.queueTag)) viewModel.playerController.togglePlayPause()
                                    else viewModel.playAll(sortedTracks.ifEmpty { popularTracks })
                                },
                                onShuffle = { viewModel.shuffleAll(sortedTracks.ifEmpty { popularTracks }) },
                                onShare = share,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }

                        if (popularTracks.isNotEmpty()) {
                            item(key = "popular_title") { DetailSectionTitle(stringResource(R.string.detail_popular)) }
                            val shown = if (popularExpanded) popularTracks else popularTracks.take(5)
                            shown.forEachIndexed { index, track ->
                                item(key = "popular_${track.id}") {
                                    ArtistTrack(
                                        track = track,
                                        queue = popularTracks,
                                        rank = index + 1,
                                        modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp).animateItem(),
                                    )
                                }
                            }
                            if (popularTracks.size > 5) {
                                item(key = "popular_more") {
                                    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                                        TextButton(onClick = { haptic(); popularExpanded = !popularExpanded }) {
                                            Text(stringResource(if (popularExpanded) R.string.detail_show_less else R.string.detail_show_more))
                                        }
                                    }
                                }
                            }
                        }

                        if (state.albums.isNotEmpty()) {
                            item(key = "albums") {
                                Column {
                                    DetailSectionTitle(stringResource(R.string.detail_albums))
                                    DetailCardCarousel(
                                        items = state.albums.map { it.toCardItem(playlistKindLabel(it)) },
                                        onClick = onPlaylistClick,
                                    )
                                }
                            }
                        }

                        if (state.playlists.isNotEmpty()) {
                            item(key = "playlists") {
                                Column {
                                    DetailSectionTitle(stringResource(R.string.search_playlists))
                                    DetailCardCarousel(
                                        items = state.playlists.map { it.toCardItem(playlistKindLabel(it)) },
                                        onClick = onPlaylistClick,
                                    )
                                }
                            }
                        }

                        item(key = "tracks_title") {
                            DetailSectionTitle(
                                text = stringResource(R.string.detail_all_tracks),
                                trailing = {
                                    if (visibleTracks.isNotEmpty()) {
                                        TrackSortButton(sort = sort, onSortChange = { sort = it })
                                    }
                                },
                            )
                        }
                        if (state.isLoadingTracks) {
                            item(key = "tracks_loading") {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    LoadingIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        items(sortedTracks, key = { it.id }) { track ->
                            ArtistTrack(
                                track = track,
                                queue = sortedTracks,
                                rank = null,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                            )
                        }

                        if (state.relatedArtists.isNotEmpty()) {
                            item(key = "related") {
                                Column {
                                    DetailSectionTitle(stringResource(R.string.detail_related_artists))
                                    DetailCardCarousel(
                                        items = state.relatedArtists.map { artist ->
                                            DetailCardItem(
                                                id = artist.id,
                                                title = artist.fullName?.ifBlank { null } ?: artist.username,
                                                subtitle = artist.followersCount?.let { followersCountText(it) },
                                                artworkUrl = artist.avatarUrl,
                                            )
                                        },
                                        onClick = onArtistClick,
                                        circular = true,
                                    )
                                }
                            }
                        }

                        if (!user.description.isNullOrBlank() || !user.city.isNullOrBlank()) {
                            item(key = "about") { ArtistAbout(user) }
                        }
                    }
                }
            }

            CollapsingDetailTopBar(
                title = displayName,
                progress = collapse,
                onBack = onBack,
            )
        }
    }

    selectedBadge?.let { badge ->
        com.savoo.scclient.ui.components.BadgeBottomSheet(
            badge = badge,
            profileName = displayName,
            onDismiss = { selectedBadge = null },
            onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        )
    }
}

@Composable
private fun playlistKindLabel(playlist: Playlist): String {
    val kind = stringResource(
        when (playlist.setType?.lowercase()) {
            "album" -> R.string.playlist_kind_album
            "ep" -> R.string.playlist_kind_ep
            "single" -> R.string.playlist_kind_single
            "compilation" -> R.string.playlist_kind_compilation
            else -> if (playlist.isAlbum == true) R.string.playlist_kind_album else R.string.playlist_kind_playlist
        }
    )
    return listOfNotNull(kind, playlist.releaseYear).joinToString(" · ")
}

private fun Playlist.toCardItem(subtitle: String) = DetailCardItem(
    id = id,
    title = title,
    subtitle = subtitle,
    artworkUrl = artworkUrl ?: tracks?.firstOrNull { it.artworkUrl != null }?.artworkUrl,
)

@Composable
private fun ArtistHero(
    user: User,
    badges: List<String>,
    showId: Boolean,
    scrollOffsetPx: Int,
    onBadgeClick: (String) -> Unit,
) {
    val background = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clipToBounds(),
    ) {
        AsyncImage(
            model = hiResArtwork(user.avatarUrl),
            contentDescription = user.username,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = scrollOffsetPx * 0.45f
                    val zoom = 1.08f - (scrollOffsetPx / 4000f).coerceAtMost(0.08f)
                    scaleX = zoom
                    scaleY = zoom
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.55f),
                        0.22f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to background,
                    )
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (user.verified == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.detail_verified_artist),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.fullName?.ifBlank { null } ?: user.username,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                badges.forEach { badge ->
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (badge == "developer") Icons.Filled.Handyman else Icons.Filled.Star,
                        contentDescription = badgeTitle(badge),
                        tint = when (badge) {
                            "developer" -> MaterialTheme.colorScheme.tertiary
                            "supporter" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onBadgeClick(badge) },
                    )
                }
            }
            if (showId) {
                Text(
                    text = stringResource(R.string.artist_id, user.id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArtistMeta(user: User, trackCount: Int) {
    val parts = listOfNotNull(
        user.followersCount?.let { followersCountText(it) },
        stringResource(R.string.artist_tracks_count, trackCount),
    )
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            stringResource(R.string.artist_at_username, user.username),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArtistAbout(user: User) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticTick()
    Column {
        DetailSectionTitle(stringResource(R.string.detail_about_artist))
        Surface(
            onClick = { haptic(); expanded = !expanded },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = hiResArtwork(user.avatarUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.fullName?.ifBlank { null } ?: user.username,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        user.city?.takeIf { it.isNotBlank() }?.let { city ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    city,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                user.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        description.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
