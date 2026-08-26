package com.savoo.scclient.ui.screens.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.FavoritesRepository
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.EmptyState
import com.savoo.scclient.ui.components.ExpressivePullToRefreshBox
import com.savoo.scclient.ui.components.FavoriteSource
import com.savoo.scclient.ui.components.TrackRow
import com.savoo.scclient.ui.components.TrackSelectionBar
import com.savoo.scclient.ui.components.TrackSort
import com.savoo.scclient.ui.components.TrackSortButton
import com.savoo.scclient.ui.components.applySortOption
import com.savoo.scclient.ui.components.rememberTrackSelection
import com.savoo.scclient.ui.haptics.rememberHapticTick
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val trackRepository: TrackRepository,
    private val favoritesRepository: FavoritesRepository,
    private val tokenStore: TokenStore,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
    val playerController: PlayerController,
    val offlineTrackManager: OfflineTrackManager,
) : ViewModel() {

    // Online likes are reconciled into this same Room table (see FavoritesRepository), so this
    // single query already includes local, online, and both-sourced favorites, sorted naturally.
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

    val trackSources = favoritesDao.getAllTracks().map { list ->
        list.associate { it.trackId to FavoriteSource.valueOf(it.source) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val onlineFavoritesEnabled = settingsRepository.settings.map { it.onlineFavoritesEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val offlineTrackIds = offlineTrackManager.getAllOfflineTracks().map { list ->
        list.map { it.trackId }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadingTrackIds = offlineTrackManager.downloadingTrackIds

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            combine(tokenStore.isLoggedIn, onlineFavoritesEnabled) { loggedIn, enabled -> loggedIn && enabled }
                .distinctUntilChanged()
                .collect { active -> if (active) refreshOnline() }
        }
    }

    fun refreshOnline() {
        if (!tokenStore.isLoggedIn.value || !onlineFavoritesEnabled.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { trackRepository.getLikedTracks() }
                .onSuccess { result ->
                    com.savoo.scclient.debug.DebugLog.log("ResonaFavorites", "fetched ${result.size} online likes")
                    favoritesRepository.syncOnlineLikes(result)
                }
                .onFailure { e ->
                    com.savoo.scclient.debug.DebugLog.log("ResonaFavorites", "failed to fetch online likes: $e")
                    _message.value = context.getString(R.string.favorites_online_fetch_failed)
                }
            _isRefreshing.value = false
        }
    }

    fun playTrack(track: Track, queue: List<Track> = tracks.value) {
        val idx = queue.indexOfFirst { it.id == track.id }
        playerController.playQueue(queue, idx.coerceAtLeast(0), tag = "favorites")
    }

    /** Every row shown here is already favorited (locally, online, or both) - tapping the heart
     * always means "remove", from whichever source(s) it's currently in. */
    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            if (favoritesDao.isTrackFavoriteSync(trackId)) favoritesDao.removeTrack(trackId)
            if (onlineFavoritesEnabled.value && tokenStore.isLoggedIn.value) {
                runCatching { trackRepository.unlikeTrack(trackId) }
                    .onFailure { _message.value = context.getString(R.string.favorites_online_unlike_failed) }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
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

    fun removeSelectedFromFavorites(ids: Set<Long>) {
        ids.forEach { toggleFavorite(it) }
    }

    fun toggleDownloadForSelected(ids: Set<Long>) {
        viewModelScope.launch {
            val currentlyOffline = offlineTrackIds.value
            val allOffline = ids.isNotEmpty() && ids.all { it in currentlyOffline }
            val selectedTracks = tracks.value.filter { it.id in ids }
            if (allOffline) {
                ids.forEach { offlineTrackManager.removeFromOffline(it) }
            } else {
                selectedTracks.filter { it.id !in currentlyOffline }.forEach { offlineTrackManager.saveForOffline(it) }
            }
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val tracks by viewModel.tracks.collectAsState()
    val trackSources by viewModel.trackSources.collectAsState()
    val onlineFavoritesEnabled by viewModel.onlineFavoritesEnabled.collectAsState()
    val offlineTrackIds by viewModel.offlineTrackIds.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    val downloadingIds by viewModel.downloadingTrackIds.collectAsState()
    val message by viewModel.message.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(TrackSort()) }
    val listState = rememberLazyListState()
    LaunchedEffect(sort) { listState.animateScrollToItem(0) }
    val selection = rememberTrackSelection()
    val haptic = rememberHapticTick()

    LaunchedEffect(Unit) { viewModel.refreshOnline() }

    fun favoriteSourceOf(trackId: Long): FavoriteSource? {
        if (!onlineFavoritesEnabled) return null
        return trackSources[trackId] ?: FavoriteSource.LOCAL
    }

    val filteredTracks = (if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.user.username.contains(searchQuery, ignoreCase = true)
        }).applySortOption(sort)

    message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (tracks.isNotEmpty()) {
                        TrackSortButton(sort = sort, onSortChange = { sort = it })
                    }
                }
            )
        },
        bottomBar = {
            TrackSelectionBar(
                selectedCount = selection.count,
                onClear = { selection.clear() },
                downloadingCount = filteredTracks.count { it.id in downloadingIds },
                onSelectAll = { selection.selectAll(filteredTracks.map { it.id }) },
                onFavoriteAll = {
                    viewModel.removeSelectedFromFavorites(selection.selectedIds)
                    selection.clear()
                },
                onDownloadAll = {
                    viewModel.toggleDownloadForSelected(selection.selectedIds)
                    selection.clear()
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ExpressivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { haptic(); viewModel.refreshOnline() },
            modifier = Modifier.padding(padding),
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (tracks.isNotEmpty()) {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (filteredTracks.isEmpty()) {
                EmptyState(
                    icon = if (tracks.isEmpty()) Icons.Filled.FavoriteBorder else Icons.Filled.Search,
                    text = if (tracks.isEmpty()) stringResource(R.string.favorites_empty)
                        else stringResource(R.string.search_nothing_found),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val isCurrentTrack = playerState.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            onClick = { viewModel.playTrack(track, filteredTracks) },
                            isLoading = playerState.loadingTrackId == track.id,
                            isFavorite = true,
                            isPlaying = playerState.isPlaying && isCurrentTrack,
                            onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                            onTogglePlayPause = {
                                if (isCurrentTrack) viewModel.playerController.togglePlayPause()
                                else viewModel.playTrack(track, filteredTracks)
                            },
                            favoriteSource = favoriteSourceOf(track.id),
                            isDownloaded = track.id in offlineTrackIds,
                            isDownloading = track.id in downloadingIds,
                            onToggleDownload = { viewModel.toggleDownload(track) },
                            selectionActive = selection.isActive,
                            isSelected = selection.contains(track.id),
                            onLongPress = { selection.toggle(track.id) },
                            modifier = Modifier.padding(bottom = 8.dp).animateItem(),
                        )
                    }
                }
            }
        }
        }
    }
}
