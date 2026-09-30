package com.savoo.scclient.ui.screens.favorites

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.R
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.LocalPlaylistSummary
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.localPlaylistRouteId
import com.savoo.scclient.data.model.toFavoritePlaylist
import com.savoo.scclient.data.repository.PlaylistsRepository
import com.savoo.scclient.ui.components.CreatePlaylistSheet
import com.savoo.scclient.data.repository.FavoritesRepository
import com.savoo.scclient.ui.components.EmptyState
import com.savoo.scclient.ui.components.UndoAction
import com.savoo.scclient.ui.components.UndoController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritePlaylistsViewModel @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val undoController: UndoController,
    private val favoritesRepository: FavoritesRepository,
    private val playlistsRepository: PlaylistsRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { favoritesRepository.fillMissingPlaylistArtwork() }
        viewModelScope.launch { playlistsRepository.refreshOwnOnline() }
    }

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val allPlaylists = favoritesDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val allLocalPlaylists = playlistsRepository.observeLocalPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasPlaylists = combine(allPlaylists, allLocalPlaylists, playlistsRepository.ownOnlinePlaylists) { favorites, local, own ->
        if (favorites == null || local == null) null else favorites.isNotEmpty() || local.isNotEmpty() || own.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ownOnlinePlaylists = combine(playlistsRepository.ownOnlinePlaylists, _query) { list, query ->
        val needle = query.trim()
        list.filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteOnline(playlistId: Long, onFailed: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { playlistsRepository.deleteOnline(playlistId) }
                .onFailure { onFailed(it.message ?: it.javaClass.simpleName) }
        }
    }

    val localPlaylists = combine(allLocalPlaylists, _query) { list, query ->
        val needle = query.trim()
        list.orEmpty().filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists = combine(allPlaylists, _query, playlistsRepository.ownOnlinePlaylists) { list, query, own ->
        val needle = query.trim()
        val ownIds = own.mapTo(HashSet()) { it.id }
        list.orEmpty().filter { it.playlistId !in ownIds }.filter {
            needle.isEmpty() ||
                it.title.contains(needle, ignoreCase = true) ||
                it.username.contains(needle, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(query: String) {
        _query.value = query
    }

    fun deleteLocal(localId: Long) {
        viewModelScope.launch { playlistsRepository.deleteLocal(localId) }
    }

    fun remove(playlist: FavoritePlaylist) {
        viewModelScope.launch {
            favoritesDao.removePlaylist(playlist.playlistId)
            undoController.show(
                UndoAction(
                    messageRes = R.string.favorite_removed,
                    icon = Icons.Filled.FavoriteBorder,
                    onUndo = { favoritesDao.addPlaylist(playlist) },
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoritePlaylistsScreen(
    onBack: () -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    viewModel: FavoritePlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    val hasPlaylists by viewModel.hasPlaylists.collectAsState()
    val query by viewModel.query.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()
    val ownOnlinePlaylists by viewModel.ownOnlinePlaylists.collectAsState()
    val context = LocalContext.current
    var deleteOnlineTarget by remember { mutableStateOf<Playlist?>(null) }

    deleteOnlineTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteOnlineTarget = null },
            title = { Text(stringResource(R.string.playlist_delete_confirm_title)) },
            text = { Text(stringResource(R.string.playlist_delete_confirm_online, target.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOnline(target.id) { message ->
                        Toast.makeText(context, context.getString(R.string.playlist_error_generic, message), Toast.LENGTH_LONG).show()
                    }
                    deleteOnlineTarget = null
                }) {
                    Text(stringResource(R.string.playlist_delete_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOnlineTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    val localLabel = stringResource(R.string.playlist_local_label)
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<LocalPlaylistSummary?>(null) }

    if (showCreate) {
        CreatePlaylistSheet(
            tracks = emptyList(),
            onDismiss = { showCreate = false },
            onCreated = { routeId -> onPlaylistClick(routeId) },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.playlist_delete_confirm_title)) },
            text = { Text(stringResource(R.string.playlist_delete_confirm_local, target.title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteLocal(target.id); deleteTarget = null }) {
                    Text(stringResource(R.string.playlist_delete_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.favorite_playlists_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.playlist_new))
                }
            },
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                hasPlaylists == null -> Unit
                hasPlaylists == false -> Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        text = stringResource(R.string.favorite_playlists_empty),
                    )
                    Button(
                        onClick = { showCreate = true },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                            .height(56.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.playlist_new), style = MaterialTheme.typography.titleMedium)
                    }
                }
                else -> {
                    CollectionSearchField(
                        query = query,
                        onQueryChange = viewModel::setQuery,
                        placeholder = stringResource(R.string.favorite_playlists_search_hint),
                    )
                    if (playlists.isEmpty() && localPlaylists.isEmpty() && ownOnlinePlaylists.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Search,
                            text = stringResource(R.string.search_nothing_found),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 156.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(localPlaylists, key = { localPlaylistRouteId(it.id) }) { local ->
                                FavoritePlaylistTile(
                                    playlist = local.toFavoritePlaylist(localLabel),
                                    onClick = { onPlaylistClick(localPlaylistRouteId(local.id)) },
                                    onRemove = { deleteTarget = local },
                                    removeLabel = stringResource(R.string.playlist_delete),
                                    removeIcon = Icons.Filled.Delete,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            items(ownOnlinePlaylists, key = { it.id }) { own ->
                                val label = listOfNotNull(
                                    stringResource(R.string.playlist_online_label),
                                    stringResource(R.string.playlist_private_label).takeIf { own.sharing == "private" },
                                ).joinToString(", ")
                                FavoritePlaylistTile(
                                    playlist = own.toFavoritePlaylist(label),
                                    onClick = { onPlaylistClick(own.id) },
                                    onRemove = { deleteOnlineTarget = own },
                                    removeLabel = stringResource(R.string.playlist_delete),
                                    removeIcon = Icons.Filled.Delete,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            items(playlists, key = { it.playlistId }) { playlist ->
                                FavoritePlaylistTile(
                                    playlist = playlist,
                                    onClick = { onPlaylistClick(playlist.playlistId) },
                                    onRemove = { viewModel.remove(playlist) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
