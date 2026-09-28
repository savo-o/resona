package com.savoo.scclient.ui.screens.favorites

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.R
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoritePlaylist
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
) : ViewModel() {

    init {
        viewModelScope.launch { favoritesRepository.fillMissingPlaylistArtwork() }
    }

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val allPlaylists = favoritesDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasPlaylists = allPlaylists.map { it?.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playlists = combine(allPlaylists, _query) { list, query ->
        val needle = query.trim()
        list.orEmpty().filter {
            needle.isEmpty() ||
                it.title.contains(needle, ignoreCase = true) ||
                it.username.contains(needle, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(query: String) {
        _query.value = query
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritePlaylistsScreen(
    onBack: () -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    viewModel: FavoritePlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    val hasPlaylists by viewModel.hasPlaylists.collectAsState()
    val query by viewModel.query.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.favorite_playlists_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                hasPlaylists == null -> Unit
                hasPlaylists == false -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    text = stringResource(R.string.favorite_playlists_empty),
                )
                else -> {
                    CollectionSearchField(
                        query = query,
                        onQueryChange = viewModel::setQuery,
                        placeholder = stringResource(R.string.favorite_playlists_search_hint),
                    )
                    if (playlists.isEmpty()) {
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
