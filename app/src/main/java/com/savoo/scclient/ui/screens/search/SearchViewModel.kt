package com.savoo.scclient.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.DeepLinkResult
import com.savoo.scclient.data.remote.SoundCloudImportRepository
import com.savoo.scclient.data.repository.SearchHistoryManager
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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
    val isResolvingLink: Boolean = false,
    val error: String? = null,
)

sealed class SearchNavEvent {
    data class Artist(val userId: Long) : SearchNavEvent()
    data class Playlist(val playlistId: Long) : SearchNavEvent()
}

private val SOUNDCLOUD_URL_REGEX = Regex("""^https?://(www\.|m\.|on\.)?soundcloud\.com/\S+""", RegexOption.IGNORE_CASE)

@OptIn(FlowPreview::class)
@UnstableApi
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TrackRepository,
    private val favoritesDao: FavoritesDao,
    private val favoritesRepository: com.savoo.scclient.data.repository.FavoritesRepository,
    private val searchHistory: SearchHistoryManager,
    private val offlineTrackManager: OfflineTrackManager,
    private val scImportRepo: SoundCloudImportRepository,
    val playerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<SearchNavEvent>()
    val navEvent: SharedFlow<SearchNavEvent> = _navEvent.asSharedFlow()

    val history = searchHistory.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val queryFlow = MutableStateFlow("")

    init {
        queryFlow
            .debounce(350)
            .distinctUntilChanged()
            .onEach { q ->
                when {
                    isSoundCloudUrl(q) -> clearResults()
                    q.isNotBlank() -> runSearch(q)
                    else -> clearResults()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun isSoundCloudUrl(query: String): Boolean = SOUNDCLOUD_URL_REGEX.matches(query.trim())

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        queryFlow.value = query
    }

    fun onQuerySubmit() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        if (isSoundCloudUrl(query)) {
            resolveLink(query)
        } else {
            viewModelScope.launch { searchHistory.add(query) }
        }
    }

    /** Pasting a soundcloud.com link and hitting search opens it directly instead of text-searching for it. */
    private fun resolveLink(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResolvingLink = true, error = null)
            scImportRepo.resolveUrl(url)
                .onSuccess { result ->
                    when (result) {
                        is DeepLinkResult.User -> _navEvent.emit(SearchNavEvent.Artist(result.userId))
                        is DeepLinkResult.Playlist -> _navEvent.emit(SearchNavEvent.Playlist(result.playlistId))
                        is DeepLinkResult.Track -> {
                            val track = runCatching { repository.getTrack(result.trackId) }.getOrNull()
                            if (track != null) {
                                playerController.playQueue(listOf(track), 0)
                            } else {
                                _uiState.value = _uiState.value.copy(error = "Track not found")
                            }
                        }
                    }
                    _uiState.value = _uiState.value.copy(isResolvingLink = false, query = "")
                    queryFlow.value = ""
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isResolvingLink = false, error = e.message ?: "Could not open link")
                }
        }
    }

    fun onTabChange(tab: SearchTab) {
        onQuerySubmit()
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

    fun selectFromHistory(query: String) {
        onQueryChange(query)
    }

    fun removeHistoryItem(query: String) {
        viewModelScope.launch { searchHistory.remove(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistory.clear() }
    }

    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        val idx = tracks.indexOfFirst { it.id == track.id }
        playerController.playQueue(tracks, idx.coerceAtLeast(0), tag = "search")
    }

    fun isFavoriteFlow(trackId: Long) = favoritesDao.isTrackFavorite(trackId)

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            favoritesRepository.toggleTrackFavorite(track)
        }
    }

    fun isOfflineFlow(trackId: Long) = offlineTrackManager.isOfflineTrack(trackId)

    fun saveForOffline(track: Track) {
        viewModelScope.launch { offlineTrackManager.saveForOffline(track) }
    }

    fun removeFromOffline(trackId: Long) {
        viewModelScope.launch { offlineTrackManager.removeFromOffline(trackId) }
    }
}
