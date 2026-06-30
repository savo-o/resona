package com.savoo.scclient.ui.screens.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.R
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.User
import com.savoo.scclient.ui.components.ArtistRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoriteArtistsViewModel @Inject constructor(
    private val favoritesDao: FavoritesDao,
) : ViewModel() {

    val artists = favoritesDao.getAllArtists().map { list ->
        list.map { fav ->
            User(
                id = fav.artistId,
                username = fav.username,
                avatarUrl = fav.avatarUrl,
                followersCount = fav.followersCount,
                fullName = fav.fullName,
                permalinkUrl = fav.permalinkUrl,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeFavorite(artistId: Long) {
        viewModelScope.launch { favoritesDao.removeArtist(artistId) }
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

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.favorite_artists_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { padding ->
        if (artists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.favorite_artists_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(artists, key = { it.id }) { user ->
                    ArtistRow(
                        user = user,
                        onClick = { onArtistClick(user.id) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}
