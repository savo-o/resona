package com.savoo.scclient.ui.screens.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.map
import com.savoo.scclient.data.local.FavoriteTrackFilter
import com.savoo.scclient.data.local.FavoriteTrackOrder
import com.savoo.scclient.data.local.FavoriteTrackQueries
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.local.SQLITE_MAX_IDS_PER_QUERY
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.toTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.model.restrictionReason
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
import com.savoo.scclient.ui.components.TrackSortOption
import com.savoo.scclient.ui.components.TrackSortButton
import com.savoo.scclient.ui.components.UndoAction
import com.savoo.scclient.ui.components.UndoController
import com.savoo.scclient.ui.components.rememberTrackSelection
import com.savoo.scclient.ui.haptics.rememberHapticTick
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import androidx.compose.ui.text.style.TextOverflow

private const val FAVORITES_PAGE_SIZE = 60
private const val SEARCH_DEBOUNCE_MS = 250L

data class FavoriteRow(
    val track: Track,
    val source: FavoriteSource,
)

private fun FavoriteTrack.toRow() = FavoriteRow(
    track = toTrack(),
    source = runCatching { FavoriteSource.valueOf(source) }.getOrDefault(FavoriteSource.LOCAL),
)

private fun TrackSort.toFilter(search: String) = FavoriteTrackFilter(
    search = search,
    order = when (option) {
        TrackSortOption.DEFAULT -> FavoriteTrackOrder.ADDED
        TrackSortOption.TITLE -> FavoriteTrackOrder.TITLE
        TrackSortOption.ARTIST -> FavoriteTrackOrder.ARTIST
        TrackSortOption.DURATION -> FavoriteTrackOrder.DURATION
    },
    descending = descending,
)

@UnstableApi
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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
    private val undoController: UndoController,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sort = MutableStateFlow(TrackSort())
    val sort = _sort.asStateFlow()

    private val filter = combine(
        _searchQuery.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        _sort,
    ) { query, sort -> sort.toFilter(query) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, FavoriteTrackFilter())

    val tracks: Flow<PagingData<FavoriteRow>> = filter
        .flatMapLatest { current ->
            Pager(PagingConfig(pageSize = FAVORITES_PAGE_SIZE, prefetchDistance = FAVORITES_PAGE_SIZE, enablePlaceholders = false)) {
                favoritesDao.pagingSource(FavoriteTrackQueries.page(current))
            }.flow
        }
        .map { paging -> paging.map { it.toRow() } }
        .cachedIn(viewModelScope)

    val totalCount = favoritesDao.observeTrackCount()
        .map<Int, Int?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSort(sort: TrackSort) {
        _sort.value = sort
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

    fun playTrack(track: Track, index: Int) {
        playerController.playFavorites(filter.value, index, track)
    }

    suspend fun filteredTrackIds(): List<Long> = withContext(Dispatchers.IO) {
        favoritesDao.queryTrackIds(FavoriteTrackQueries.ids(filter.value))
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch { removeWithUndo(setOf(trackId)) }
    }

    private suspend fun removeFavorite(trackId: Long) {
        if (favoritesDao.isTrackFavoriteSync(trackId)) favoritesDao.removeTrack(trackId)
        if (onlineFavoritesEnabled.value && tokenStore.isLoggedIn.value) {
            runCatching { trackRepository.unlikeTrack(trackId) }
                .onFailure { _message.value = context.getString(R.string.favorites_online_unlike_failed) }
        }
    }

    private suspend fun loadRows(ids: Collection<Long>): List<FavoriteTrack> = withContext(Dispatchers.IO) {
        ids.chunked(SQLITE_MAX_IDS_PER_QUERY).flatMap { favoritesDao.getTracksByIds(it) }
    }

    private suspend fun loadSelectedInOrder(ids: Set<Long>): List<Track> {
        val ordered = filteredTrackIds().filter { it in ids }
        val byId = loadRows(ordered).associateBy { it.trackId }
        return ordered.mapNotNull { byId[it]?.toTrack() }
    }

    private suspend fun removeWithUndo(ids: Set<Long>) {
        val removed = loadRows(ids)
        if (removed.isEmpty()) return
        val online = onlineFavoritesEnabled.value && tokenStore.isLoggedIn.value
        val repository = favoritesRepository
        if (online) {
            removed.forEach { removeFavorite(it.trackId) }
        } else {
            withContext(Dispatchers.IO) {
                removed.map { it.trackId }.chunked(SQLITE_MAX_IDS_PER_QUERY).forEach { favoritesDao.removeTracks(it) }
            }
        }
        undoController.show(
            UndoAction(
                messageRes = if (removed.size == 1) R.string.favorite_removed else R.string.favorites_removed_count,
                messageArgs = if (removed.size == 1) emptyList() else listOf(removed.size),
                icon = Icons.Filled.FavoriteBorder,
                onUndo = {
                    if (online) {
                        removed.forEach { repository.restoreTrackFavorite(it.toTrack(), it) }
                    } else {
                        withContext(Dispatchers.IO) {
                            removed.chunked(SQLITE_MAX_IDS_PER_QUERY).forEach { favoritesDao.addTracks(it) }
                        }
                    }
                },
            )
        )
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

    fun queueSelected(ids: Set<Long>) {
        viewModelScope.launch {
            playerController.addToQueue(loadSelectedInOrder(ids))
        }
    }

    fun removeSelectedFromFavorites(ids: Set<Long>) {
        viewModelScope.launch { removeWithUndo(ids) }
    }

    fun toggleDownloadForSelected(ids: Set<Long>) {
        viewModelScope.launch {
            val currentlyOffline = offlineTrackIds.value
            val allOffline = ids.isNotEmpty() && ids.all { it in currentlyOffline }
            if (allOffline) {
                ids.forEach { offlineTrackManager.removeFromOffline(it) }
            } else {
                val selectedTracks = loadSelectedInOrder(ids)
                offlineTrackManager.enqueueDownloads(selectedTracks.filter { it.id !in currentlyOffline })
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
    val lazyTracks = viewModel.tracks.collectAsLazyPagingItems()
    val totalCount by viewModel.totalCount.collectAsState()
    val onlineFavoritesEnabled by viewModel.onlineFavoritesEnabled.collectAsState()
    val offlineTrackIds by viewModel.offlineTrackIds.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    val unavailableReasons by viewModel.playerController.unavailableReasons.collectAsState()
    val downloadingIds by viewModel.downloadingTrackIds.collectAsState()
    val message by viewModel.message.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    LaunchedEffect(sort) { listState.animateScrollToItem(0) }
    val selection = rememberTrackSelection()
    val haptic = rememberHapticTick()
    val scope = rememberCoroutineScope()
    val hasFavorites = (totalCount ?: 0) > 0

    LaunchedEffect(Unit) { viewModel.refreshOnline() }

    fun favoriteSourceOf(row: FavoriteRow): FavoriteSource? =
        if (onlineFavoritesEnabled) row.source else null

    message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (hasFavorites) {
                        TrackSortButton(sort = sort, onSortChange = { viewModel.setSort(it) })
                    }
                }
            )
        },
        bottomBar = {
            TrackSelectionBar(
                selectedCount = selection.count,
                onClear = { selection.clear() },
                onQueueAll = {
                    viewModel.queueSelected(selection.selectedIds)
                    selection.clear()
                },
                onSelectAll = { scope.launch { selection.selectAll(viewModel.filteredTrackIds()) } },
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
            if (hasFavorites) {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            val refreshing = lazyTracks.loadState.refresh is LoadState.Loading
            when {
                totalCount == null || (hasFavorites && lazyTracks.itemCount == 0 && refreshing) -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                !hasFavorites -> EmptyState(
                    icon = Icons.Filled.FavoriteBorder,
                    text = stringResource(R.string.favorites_empty),
                )
                lazyTracks.itemCount == 0 -> EmptyState(
                    icon = Icons.Filled.Search,
                    text = stringResource(R.string.search_nothing_found),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(
                        count = lazyTracks.itemCount,
                        key = lazyTracks.itemKey { it.track.id },
                    ) { index ->
                        val row = lazyTracks[index] ?: return@items
                        val track = row.track
                        val isCurrentTrack = playerState.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            onClick = { viewModel.playTrack(track, index) },
                            isLoading = playerState.loadingTrackId == track.id,
                            isFavorite = true,
                            isPlaying = playerState.isPlaying && isCurrentTrack,
                            onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                            onTogglePlayPause = {
                                if (isCurrentTrack) viewModel.playerController.togglePlayPause()
                                else viewModel.playTrack(track, index)
                            },
                            favoriteSource = favoriteSourceOf(row),
                            isDownloaded = track.id in offlineTrackIds,
                            isDownloading = track.id in downloadingIds,
                            onToggleDownload = { viewModel.toggleDownload(track) },
                            selectionActive = selection.isActive,
                            isSelected = selection.contains(track.id),
                            onLongPress = { selection.toggle(track.id) },
                            unavailableReason = unavailableReasons[track.id] ?: track.restrictionReason(),
                            modifier = Modifier.padding(bottom = 8.dp).animateItem(),
                        )
                    }
                }
            }
        }
        }
    }
}
