package com.savoo.scclient.ui.screens.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.savoo.scclient.ui.components.FavoriteSource
import com.savoo.scclient.ui.components.TrackRow
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

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        viewModelScope.launch {
            combine(tokenStore.isLoggedIn, onlineFavoritesEnabled) { loggedIn, enabled -> loggedIn && enabled }
                .distinctUntilChanged()
                .collect { active -> if (active) refreshOnline() }
        }
    }

    fun refreshOnline() {
        viewModelScope.launch {
            runCatching { trackRepository.getLikedTracks() }
                .onSuccess { result ->
                    com.savoo.scclient.debug.DebugLog.log("ResonaFavorites", "fetched ${result.size} online likes")
                    favoritesRepository.syncOnlineLikes(result)
                }
                .onFailure { e ->
                    com.savoo.scclient.debug.DebugLog.log("ResonaFavorites", "failed to fetch online likes: $e")
                    _message.value = context.getString(R.string.favorites_online_fetch_failed)
                }
        }
    }

    fun playTrack(track: Track) {
        val currentTracks = tracks.value
        val idx = currentTracks.indexOfFirst { it.id == track.id }
        playerController.playQueue(currentTracks, idx.coerceAtLeast(0), tag = "favorites")
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
    val playerState by viewModel.playerController.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshOnline() }

    fun favoriteSourceOf(trackId: Long): FavoriteSource? {
        if (!onlineFavoritesEnabled) return null
        return trackSources[trackId] ?: FavoriteSource.LOCAL
    }

    val filteredTracks = if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.user.username.contains(searchQuery, ignoreCase = true)
        }

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
                }
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
                    icon = if (tracks.isEmpty()) Icons.Filled.FavoriteBorder else Icons.Filled.Search,
                    text = if (tracks.isEmpty()) stringResource(R.string.favorites_empty)
                        else stringResource(R.string.search_nothing_found),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val isCurrentTrack = playerState.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            onClick = { viewModel.playTrack(track) },
                            isLoading = playerState.loadingTrackId == track.id,
                            isFavorite = true,
                            isPlaying = playerState.isPlaying && isCurrentTrack,
                            onToggleFavorite = { viewModel.toggleFavorite(track.id) },
                            onTogglePlayPause = {
                                if (isCurrentTrack) viewModel.playerController.togglePlayPause()
                                else viewModel.playTrack(track)
                            },
                            favoriteSource = favoriteSourceOf(track.id),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
