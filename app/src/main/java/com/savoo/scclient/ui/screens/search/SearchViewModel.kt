package com.savoo.scclient.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
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

enum class SearchTab(val labelRes: Int) {
    TRACKS(R.string.tab_tracks),
    ARTISTS(R.string.tab_artists),
    ALBUMS(R.string.tab_playlists),
}

data class SearchUiState(
    val query: String = "",
    val activeTab: SearchTab = SearchTab.TRACKS,
    val tracks: List<Track> = emptyList(),
    val artists: List<User> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isResolvingLink: Boolean = false,
    val error: SearchError? = null,
    val nextTracksHref: String? = null,
    val nextArtistsHref: String? = null,
    val nextAlbumsHref: String? = null,
    val resultsQuery: String? = null,
)

sealed class SearchNavEvent {
    data class Artist(val userId: Long) : SearchNavEvent()
    data class Playlist(val playlistId: Long) : SearchNavEvent()
}

private val SOUNDCLOUD_URL_REGEX = Regex("""^https?://(www\.|m\.|on\.)?soundcloud\.com/\S+""", RegexOption.IGNORE_CASE)
private val WHITESPACE_RUN = Regex("""\s+""")

private fun normalizeQuery(query: String): String = query.trim().replace(WHITESPACE_RUN, " ")

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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val history = searchHistory.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val queryFlow = MutableStateFlow("")
    private var pendingHistoryQuery: String? = null
    private var loadMoreJob: kotlinx.coroutines.Job? = null

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
        queryFlow.value = normalizeQuery(query)
    }

    fun onQuerySubmit() {
        val query = normalizeQuery(_uiState.value.query)
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
                                _uiState.value = _uiState.value.copy(
                                    error = SearchError(SearchErrorKind.TRACK_GONE)
                                )
                            }
                        }
                    }
                    _uiState.value = _uiState.value.copy(isResolvingLink = false, query = "")
                    queryFlow.value = ""
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isResolvingLink = false,
                        error = SearchError(SearchErrorKind.LINK, e.message),
                    )
                }
        }
    }

    fun onTabChange(tab: SearchTab) {
        onQuerySubmit()
        _uiState.value = _uiState.value.copy(activeTab = tab)
        val query = normalizeQuery(_uiState.value.query)
        if (query.isNotBlank()) runSearch(query)
    }

    private fun clearResults() {
        loadMoreJob?.cancel()
        _uiState.value = _uiState.value.copy(
            tracks = emptyList(),
            artists = emptyList(),
            albums = emptyList(),
            isLoadingMore = false,
            nextTracksHref = null,
            nextArtistsHref = null,
            nextAlbumsHref = null,
            resultsQuery = null,
            isLoading = false,
        )
    }

    private fun isStale(query: String): Boolean = normalizeQuery(_uiState.value.query) != query

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return
        val tab = state.activeTab
        val nextHref = when (tab) {
            SearchTab.TRACKS -> state.nextTracksHref
            SearchTab.ARTISTS -> state.nextArtistsHref
            SearchTab.ALBUMS -> state.nextAlbumsHref
        } ?: return
        val query = normalizeQuery(state.query)

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            runCatching {
                when (tab) {
                    SearchTab.TRACKS -> repository.searchTracksPage(nextHref)
                    SearchTab.ARTISTS -> repository.searchUsersPage(nextHref)
                    SearchTab.ALBUMS -> repository.searchPlaylistsPage(nextHref)
                }
            }.onSuccess { page ->
                val current = _uiState.value
                if (normalizeQuery(current.query) != query || current.activeTab != tab) {
                    _uiState.value = current.copy(isLoadingMore = false)
                    return@onSuccess
                }
                _uiState.value = when (tab) {
                    SearchTab.TRACKS -> {
                        @Suppress("UNCHECKED_CAST")
                        val items = page.items as List<Track>
                        val added = items.filterNot { new -> current.tracks.any { it.id == new.id } }
                        current.copy(
                            tracks = current.tracks + added,
                            isLoadingMore = false,
                            nextTracksHref = page.nextHref.takeIf { added.isNotEmpty() },
                        )
                    }
                    SearchTab.ARTISTS -> {
                        @Suppress("UNCHECKED_CAST")
                        val items = page.items as List<User>
                        val added = items.filterNot { new -> current.artists.any { it.id == new.id } }
                        current.copy(
                            artists = current.artists + added,
                            isLoadingMore = false,
                            nextArtistsHref = page.nextHref.takeIf { added.isNotEmpty() },
                        )
                    }
                    SearchTab.ALBUMS -> {
                        @Suppress("UNCHECKED_CAST")
                        val items = page.items as List<Playlist>
                        val added = items.filterNot { new -> current.albums.any { it.id == new.id } }
                        current.copy(
                            albums = current.albums + added,
                            isLoadingMore = false,
                            nextAlbumsHref = page.nextHref.takeIf { added.isNotEmpty() },
                        )
                    }
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
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
                if (pendingHistoryQuery == query) {
                    pendingHistoryQuery = null
                    if (tracks.items.isNotEmpty() || artists.items.isNotEmpty() || albums.items.isNotEmpty()) {
                        searchHistory.add(query)
                    }
                }
                if (isStale(query)) return@onSuccess
                _uiState.value = _uiState.value.copy(
                    tracks = tracks.items,
                    artists = artists.items,
                    albums = albums.items,
                    isLoading = false,
                    isLoadingMore = false,
                    resultsQuery = query,
                    nextTracksHref = tracks.nextHref,
                    nextArtistsHref = artists.nextHref,
                    nextAlbumsHref = albums.nextHref,
                )
            }.onFailure { e ->
                if (pendingHistoryQuery == query) pendingHistoryQuery = null
                if (isStale(query)) return@onFailure
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toSearchError())
            }
        }
    }

    /** Pull-to-refresh: re-runs the current query without touching [SearchUiState.isLoading], so the
     * results stay visible under the refresh indicator instead of being swapped for a full-screen spinner. */
    fun refreshResults() {
        val query = normalizeQuery(_uiState.value.query)
        if (query.isBlank() || isSoundCloudUrl(query)) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching {
                val tracks = repository.searchTracks(query)
                val artists = repository.searchUsers(query)
                val albums = repository.searchPlaylists(query)
                Triple(tracks, artists, albums)
            }.onSuccess { (tracks, artists, albums) ->
                if (isStale(query)) return@onSuccess
                _uiState.value = _uiState.value.copy(
                    tracks = tracks.items,
                    artists = artists.items,
                    albums = albums.items,
                    error = null,
                    resultsQuery = query,
                    nextTracksHref = tracks.nextHref,
                    nextArtistsHref = artists.nextHref,
                    nextAlbumsHref = albums.nextHref,
                )
            }.onFailure { e ->
                if (isStale(query)) return@onFailure
                _uiState.value = _uiState.value.copy(error = e.toSearchError())
            }
            _isRefreshing.value = false
        }
    }

    fun selectFromHistory(query: String) {
        onQueryChange(query)
    }

    fun commitQueryToHistory() {
        val state = _uiState.value
        val query = normalizeQuery(state.query)
        if (query.isBlank() || isSoundCloudUrl(query)) return
        if (state.resultsQuery == query && !state.isLoading) {
            if (state.tracks.isNotEmpty() || state.artists.isNotEmpty() || state.albums.isNotEmpty()) {
                viewModelScope.launch { searchHistory.add(query) }
            }
        } else {
            pendingHistoryQuery = query
        }
    }

    fun removeHistoryItem(query: String) {
        viewModelScope.launch { searchHistory.remove(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistory.clear() }
    }

    fun playTrack(track: Track) {
        commitQueryToHistory()
        val tracks = _uiState.value.tracks
        val idx = tracks.indexOfFirst { it.id == track.id }
        playerController.playQueue(tracks, idx.coerceAtLeast(0), tag = "search")
    }

    fun isFavoriteFlow(trackId: Long) = favoritesDao.isTrackFavorite(trackId)

    fun toggleFavorite(track: Track) {
        commitQueryToHistory()
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
