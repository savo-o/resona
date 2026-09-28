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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
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
import com.savoo.scclient.data.model.FavoriteArtist
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
class FavoriteArtistsViewModel @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val undoController: UndoController,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val allArtists = favoritesDao.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasArtists = allArtists.map { it?.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val artists = combine(allArtists, _query) { list, query ->
        val needle = query.trim()
        list.orEmpty().filter {
            needle.isEmpty() ||
                it.username.contains(needle, ignoreCase = true) ||
                it.fullName?.contains(needle, ignoreCase = true) == true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(query: String) {
        _query.value = query
    }

    fun remove(artist: FavoriteArtist) {
        viewModelScope.launch {
            favoritesDao.removeArtist(artist.artistId)
            undoController.show(
                UndoAction(
                    messageRes = R.string.favorite_removed,
                    icon = Icons.Filled.FavoriteBorder,
                    onUndo = { favoritesDao.addArtist(artist) },
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteArtistsScreen(
    onBack: () -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    viewModel: FavoriteArtistsViewModel = hiltViewModel(),
) {
    val artists by viewModel.artists.collectAsState()
    val hasArtists by viewModel.hasArtists.collectAsState()
    val query by viewModel.query.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.favorite_artists_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                hasArtists == null -> Unit
                hasArtists == false -> EmptyState(
                    icon = Icons.Filled.Person,
                    text = stringResource(R.string.favorite_artists_empty),
                )
                else -> {
                    CollectionSearchField(
                        query = query,
                        onQueryChange = viewModel::setQuery,
                        placeholder = stringResource(R.string.favorite_artists_search_hint),
                    )
                    if (artists.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Search,
                            text = stringResource(R.string.search_nothing_found),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 112.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(artists, key = { it.artistId }) { artist ->
                                FavoriteArtistTile(
                                    artist = artist,
                                    onClick = { onArtistClick(artist.artistId) },
                                    onRemove = { viewModel.remove(artist) },
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
