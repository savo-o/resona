package com.savoo.scclient.ui.screens.offline

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.FavoritesRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.EmptyState
import com.savoo.scclient.ui.components.TrackRow
import com.savoo.scclient.ui.components.TrackSelectionBar
import com.savoo.scclient.ui.components.TrackSort
import com.savoo.scclient.ui.components.TrackSortButton
import com.savoo.scclient.ui.components.applySortOption
import com.savoo.scclient.ui.components.rememberTrackSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class OfflineTracksViewModel @Inject constructor(
    private val offlineTrackManager: OfflineTrackManager,
    private val favoritesDao: FavoritesDao,
    private val favoritesRepository: FavoritesRepository,
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

    val favoriteTrackIds = favoritesDao.getAllTracks().map { list ->
        list.map { it.trackId }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    private val _importResult = MutableStateFlow<OfflineTrackManager.LocalImportResult?>(null)
    val importResult = _importResult.asStateFlow()

    private val _watchedFolders = MutableStateFlow<List<Uri>>(emptyList())
    val watchedFolders = _watchedFolders.asStateFlow()

    init {
        refreshWatchedFoldersList()
        viewModelScope.launch {
            _isImporting.value = true
            val result = offlineTrackManager.refreshWatchedFolders()
            _isImporting.value = false
            if (result.imported > 0) _importResult.value = result
        }
    }

    private fun refreshWatchedFoldersList() {
        _watchedFolders.value = offlineTrackManager.getWatchedFolders()
    }

    fun watchedFolderDisplayName(treeUri: Uri): String = offlineTrackManager.watchedFolderDisplayName(treeUri)

    fun unwatchFolder(treeUri: Uri) {
        viewModelScope.launch {
            offlineTrackManager.removeWatchedFolder(treeUri)
            refreshWatchedFoldersList()
        }
    }

    fun playTrack(track: Track, queue: List<Track> = tracks.value) {
        val idx = queue.indexOfFirst { it.id == track.id }
        playerController.playQueue(queue, idx.coerceAtLeast(0), tag = "offline")
    }

    fun removeFromOffline(trackId: Long) {
        viewModelScope.launch {
            offlineTrackManager.removeFromOffline(trackId)
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { favoritesRepository.toggleTrackFavorite(track) }
    }

    fun removeSelectedFromOffline(ids: Set<Long>) {
        viewModelScope.launch { ids.forEach { offlineTrackManager.removeFromOffline(it) } }
    }

    fun toggleFavoriteForSelected(ids: Set<Long>) {
        viewModelScope.launch {
            val currentlyFavorite = favoriteTrackIds.value
            val allFavorite = ids.isNotEmpty() && ids.all { it in currentlyFavorite }
            val selectedTracks = tracks.value.filter { it.id in ids }
            if (allFavorite) {
                selectedTracks.forEach { favoritesRepository.toggleTrackFavorite(it) }
            } else {
                selectedTracks.filter { it.id !in currentlyFavorite }.forEach { favoritesRepository.toggleTrackFavorite(it) }
            }
        }
    }

    fun importLocalFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importResult.value = offlineTrackManager.importLocalFolder(treeUri)
            _isImporting.value = false
            refreshWatchedFoldersList()
        }
    }

    fun consumeImportResult() {
        _importResult.value = null
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OfflineTracksScreen(
    viewModel: OfflineTracksViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val tracks by viewModel.tracks.collectAsState()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsState()
    val playerState by viewModel.playerController.state.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val watchedFolders by viewModel.watchedFolders.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showWatchedFolders by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(TrackSort()) }
    val listState = rememberLazyListState()
    LaunchedEffect(sort) { listState.animateScrollToItem(0) }
    val selection = rememberTrackSelection()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.importLocalFolder(uri)
    }

    val importResultMsg = stringResource(R.string.offline_import_result)
    val importResultNoSkipMsg = stringResource(R.string.offline_import_result_no_skip)
    LaunchedEffect(importResult) {
        val result = importResult ?: return@LaunchedEffect
        val message = if (result.skipped > 0) {
            importResultMsg.format(result.imported, result.skipped)
        } else {
            importResultNoSkipMsg.format(result.imported)
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
        viewModel.consumeImportResult()
    }

    val filteredTracks = (if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.user.username.contains(searchQuery, ignoreCase = true)
        }).applySortOption(sort)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_offline)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isImporting) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        if (tracks.isNotEmpty()) {
                            TrackSortButton(sort = sort, onSortChange = { sort = it })
                        }
                        if (watchedFolders.isNotEmpty()) {
                            IconButton(onClick = { showWatchedFolders = true }) {
                                Icon(Icons.Filled.Folder, contentDescription = stringResource(R.string.offline_watched_folders_title))
                            }
                        }
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.offline_import_folder))
                        }
                    }
                }
            )
        },
        bottomBar = {
            TrackSelectionBar(
                selectedCount = selection.count,
                onClear = { selection.clear() },
                onSelectAll = { selection.selectAll(filteredTracks.map { it.id }) },
                onFavoriteAll = {
                    viewModel.toggleFavoriteForSelected(selection.selectedIds)
                    selection.clear()
                },
                onDownloadAll = {
                    viewModel.removeSelectedFromOffline(selection.selectedIds)
                    selection.clear()
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
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
                    icon = if (tracks.isEmpty()) Icons.Filled.CloudDownload else Icons.Filled.Search,
                    text = if (tracks.isEmpty()) stringResource(R.string.offline_empty)
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
                            isPlaying = playerState.isPlaying && isCurrentTrack,
                            onTogglePlayPause = {
                                if (isCurrentTrack) viewModel.playerController.togglePlayPause()
                                else viewModel.playTrack(track, filteredTracks)
                            },
                            isFavorite = track.id in favoriteTrackIds,
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            isDownloaded = true,
                            onToggleDownload = { viewModel.removeFromOffline(track.id) },
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

    if (showWatchedFolders) {
        AlertDialog(
            onDismissRequest = { showWatchedFolders = false },
            title = { Text(stringResource(R.string.offline_watched_folders_title)) },
            text = {
                if (watchedFolders.isEmpty()) {
                    Text(stringResource(R.string.offline_watched_folders_empty))
                } else {
                    Column {
                        watchedFolders.forEach { folderUri ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    viewModel.watchedFolderDisplayName(folderUri),
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                                IconButton(onClick = {
                                    viewModel.unwatchFolder(folderUri)
                                    if (watchedFolders.size <= 1) showWatchedFolders = false
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.offline_unwatch_folder))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWatchedFolders = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}
