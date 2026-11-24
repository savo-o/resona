package com.savoo.scclient.ui.screens.offline

import com.savoo.scclient.ui.navigation.aboveDock
import com.savoo.scclient.ui.navigation.withDock
import com.savoo.scclient.compat.*
import com.savoo.scclient.compat.Uri
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.resources.*
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.saveable.rememberSaveable
import com.savoo.scclient.debug.DebugLog
import com.savoo.scclient.player.exportOfflineAudio
import com.savoo.scclient.ui.components.TaskProgressBanner
import com.savoo.scclient.ui.haptics.rememberHapticTick
import com.savoo.scclient.ui.screens.playlist.PickTrackRow
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class OfflineExportProgress(
    val done: Int,
    val total: Int,
    val title: String?,
    val currentFraction: Float = 0f,
) {
    val fraction: Float get() = (done + currentFraction) / total
}

data class OfflineExportResult(val saved: Int, val failed: Int)

class OfflineTracksViewModel(
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

    val favoriteTrackIds = favoritesDao.observeTrackIds().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _exportProgress = MutableStateFlow<OfflineExportProgress?>(null)
    val exportProgress = _exportProgress.asStateFlow()
    private val _exportResult = MutableStateFlow<OfflineExportResult?>(null)
    val exportResult = _exportResult.asStateFlow()
    private var exportJob: Job? = null

    fun cancelExport() { exportJob?.cancel() }

    fun consumeExportResult() { _exportResult.value = null }

    fun exportTracks(ids: List<Long>) {
        if (ids.isEmpty() || exportJob?.isActive == true) return
        val titles = tracks.value.associate { it.id to it.title }
        exportJob = viewModelScope.launch {
            val folder = File(AppContext.cacheDir, "audio-export-${Random.nextLong().toULong()}")
            val prepared = mutableListOf<File>()
            var failed = 0
            try {
                withContext(Dispatchers.Default) { folder.mkdirs() }
                ids.forEachIndexed { index, id ->
                    val progress = OfflineExportProgress(index, ids.size, titles[id])
                    _exportProgress.value = progress
                    try {
                        val file = withContext(Dispatchers.Default) {
                            val track = offlineTrackManager.getOfflineTrack(id) ?: throw IOException("Track is not downloaded")
                            val source = File(track.localPath)
                            if (!source.exists()) throw IOException("Offline audio file is missing")
                            val name = "${track.username} - ${track.title}"
                                .map { if (it in "\\/:*?\"<>|" || it.code < 32) '_' else it }
                                .take(58).joinToString("")
                            File(folder, "$name [$id].m4a").also { target ->
                                exportOfflineAudio(track, source, target) { fraction ->
                                    _exportProgress.value = progress.copy(currentFraction = fraction)
                                }
                            }
                        }
                        prepared += file
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        DebugLog.log("AudioExport", "Export $id failed: ${e.message}")
                        failed++
                    }
                }
                _exportProgress.value = null
                val saved = prepared.isNotEmpty() && suspendCancellableCoroutine { continuation ->
                    FilePickers.exportFiles(prepared.map { it.path }) { exported ->
                        if (continuation.isActive) continuation.resume(exported)
                    }
                }
                if (saved || failed > 0) {
                    _exportResult.value = OfflineExportResult(if (saved) prepared.size else 0, failed)
                }
            } finally {
                _exportProgress.value = null
                withContext(NonCancellable + Dispatchers.Default) { folder.deleteRecursively() }
            }
        }
    }

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OfflineTracksScreen(
    viewModel: OfflineTracksViewModel = koinViewModel(),
    onBack: () -> Unit = {},
) {
    val tracks by viewModel.tracks.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var selectedExportIds by rememberSaveable { mutableStateOf(longArrayOf()) }
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

    val exportMessage = exportResult?.let { stringResource(Res.string.offline_audio_export_result, it.saved, it.failed) }
    LaunchedEffect(exportMessage) {
        if (exportMessage != null) {
            snackbarHostState.showSnackbar(exportMessage)
            viewModel.consumeExportResult()
        }
    }
    if (showExportSheet) {
        OfflineExportSheet(
            tracks = tracks.applySortOption(sort),
            selectedIds = selectedExportIds.toSet(),
            onToggleTrack = { id ->
                val selected = selectedExportIds.toSet()
                selectedExportIds = (if (id in selected) selected - id else selected + id).toLongArray()
            },
            onDismiss = { showExportSheet = false },
            onExport = { ids ->
                showExportSheet = false
                viewModel.exportTracks(ids)
            },
        )
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.importLocalFolder(uri)
    }

    val importResultMsg = stringResource(Res.string.offline_import_result)
    val importResultNoSkipMsg = stringResource(Res.string.offline_import_result_no_skip)
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

    val sortedTracks = tracks.applySortOption(sort)
    val filteredTracks = (if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.user.username.contains(searchQuery, ignoreCase = true)
        }).applySortOption(sort)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.library_offline), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                actions = {
                    if (tracks.isNotEmpty()) {
                        IconButton(enabled = exportProgress == null, onClick = {
                            selectedExportIds = selection.selectedIds.toLongArray()
                            showExportSheet = true
                        }) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = stringResource(Res.string.offline_audio_export))
                        }
                    }
                    if (isImporting) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        if (tracks.isNotEmpty()) {
                            TrackSortButton(sort = sort, onSortChange = { sort = it })
                        }
                        if (watchedFolders.isNotEmpty()) {
                            IconButton(onClick = { showWatchedFolders = true }) {
                                Icon(Icons.Filled.Folder, contentDescription = stringResource(Res.string.offline_watched_folders_title))
                            }
                        }
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(Res.string.offline_import_folder))
                        }
                    }
                }
            )
        },
        bottomBar = {
            TrackSelectionBar(
                selectedCount = selection.count,
                onClear = { selection.clear() },
                onQueueAll = {
                    viewModel.playerController.addToQueue(filteredTracks.filter { it.id in selection.selectedIds })
                    selection.clear()
                },
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
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.aboveDock()) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            exportProgress?.let { progress ->
                TaskProgressBanner(
                    icon = Icons.Filled.SaveAlt,
                    title = stringResource(Res.string.offline_audio_export_progress, progress.done + 1, progress.total),
                    subtitle = progress.title,
                    fraction = progress.fraction,
                    finished = false,
                    failed = false,
                    onCancel = viewModel::cancelExport,
                    cancelDescription = stringResource(Res.string.cancel),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (tracks.isNotEmpty()) {
                SearchBarDefaults.InputField(
                    colors = SearchBarDefaults.filledInputFieldColors(),
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = hideKeyboardOnSearch(),
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(Res.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(Res.string.search_clear))
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
                    text = if (tracks.isEmpty()) stringResource(Res.string.offline_empty)
                        else stringResource(Res.string.search_nothing_found),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp).withDock(),
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val isCurrentTrack = playerState.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            onClick = { viewModel.playTrack(track, sortedTracks) },
                            isLoading = playerState.loadingTrackId == track.id,
                            isPlaying = playerState.isPlaying && isCurrentTrack,
                            onTogglePlayPause = {
                                if (isCurrentTrack) viewModel.playerController.togglePlayPause()
                                else viewModel.playTrack(track, sortedTracks)
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
            title = { Text(stringResource(Res.string.offline_watched_folders_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                if (watchedFolders.isEmpty()) {
                    Text(stringResource(Res.string.offline_watched_folders_empty))
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
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.offline_unwatch_folder))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWatchedFolders = false }) {
                    Text(stringResource(Res.string.close))
                }
            },
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OfflineExportSheet(
    tracks: List<Track>,
    selectedIds: Set<Long>,
    onToggleTrack: (Long) -> Unit,
    onDismiss: () -> Unit,
    onExport: (List<Long>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val haptic = rememberHapticTick()
    val selectedTracks = remember(tracks, selectedIds) { tracks.filter { it.id in selectedIds } }
    val visibleTracks = remember(tracks, query) {
        val filter = query.trim()
        tracks.filter { it.title.contains(filter, ignoreCase = true) || it.user.username.contains(filter, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(
                stringResource(Res.string.offline_audio_export),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.playlist_pick_filter_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(Res.string.search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(Res.string.selection_count, selectedTracks.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
            if (visibleTracks.isEmpty()) {
                Text(
                    stringResource(if (query.isBlank()) Res.string.offline_empty else Res.string.search_nothing_found),
                    modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(visibleTracks, key = { it.id }) { track ->
                        PickTrackRow(
                            track = track,
                            selected = track.id in selectedIds,
                            alreadyAdded = false,
                            onClick = { haptic(); onToggleTrack(track.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onExport(selectedTracks.map { it.id }) },
                enabled = selectedTracks.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    if (selectedTracks.isEmpty()) stringResource(Res.string.offline_audio_export)
                    else stringResource(Res.string.offline_audio_export_selected, selectedTracks.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(
                onClick = { onExport(tracks.map { it.id }) },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.offline_audio_export_all))
            }
        }
    }
}
