package com.savoo.scclient.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab(val label: String) { TRACKS("Tracks"), ARTISTS("Artists"), ALBUMS("Playlists") }

data class SearchUiState(
    val query: String = "",
    val activeTab: SearchTab = SearchTab.TRACKS,
    val tracks: List<Track> = emptyList(),
    val artists: List<User> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@UnstableApi
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val favoritesDao: FavoritesDao,
    val playerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        queryFlow
            .debounce(350)
            .distinctUntilChanged()
            .onEach { q -> if (q.isNotBlank()) runSearch(q) else clearResults() }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        queryFlow.value = query
    }

    fun onTabChange(tab: SearchTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
        if (_uiState.value.query.isNotBlank()) runSearch(_uiState.value.query)
    }

    private fun clearResults() {
        _uiState.value = _uiState.value.copy(
            tracks = emptyList(), artists = emptyList(), albums = emptyList()
        )
    }

    private fun runSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val tracks = repository.searchTracks(query)
                val artists = repository.searchUsers(query)
                val albums = repository.searchPlaylists(query)
                Triple(tracks, artists, albums)
            }.onSuccess { (tracks, artists, albums) ->
                _uiState.value = _uiState.value.copy(
                    tracks = tracks, artists = artists, albums = albums, isLoading = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        val idx = tracks.indexOfFirst { it.id == track.id }
        playerController.playQueue(tracks, idx.coerceAtLeast(0))
    }

    fun isFavoriteFlow(trackId: Long) = favoritesDao.isTrackFavorite(trackId)

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            if (favoritesDao.isTrackFavoriteSync(track.id)) {
                favoritesDao.removeTrack(track.id)
            } else {
                favoritesDao.addTrack(
                    FavoriteTrack(
                        trackId = track.id,
                        title = track.title,
                        username = track.user.username,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs,
                        permalinkUrl = track.permalinkUrl,
                        userId = track.user.id,
                        userAvatarUrl = track.user.avatarUrl,
                    )
                )
            }
        }
    }
}
