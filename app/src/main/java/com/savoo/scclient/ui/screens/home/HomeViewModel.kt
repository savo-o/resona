package com.savoo.scclient.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun FavoriteTrack.toTrack() = Track(
    id = trackId,
    title = title,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    user = User(id = userId, username = username, avatarUrl = userAvatarUrl),
    permalinkUrl = permalinkUrl,
)

private fun OfflineTrack.toTrack() = Track(
    id = trackId,
    title = title,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    user = User(id = userId, username = username, avatarUrl = userAvatarUrl),
    permalinkUrl = permalinkUrl,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    val playerController: PlayerController,
    favoritesDao: FavoritesDao,
    offlineTrackManager: OfflineTrackManager,
    private val tokenStore: TokenStore,
    private val trackRepository: TrackRepository,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()

    val favoriteTracks = favoritesDao.getAllTracks()
        .map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteArtists: kotlinx.coroutines.flow.StateFlow<List<FavoriteArtist>> = favoritesDao.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineTracks = offlineTrackManager.getAllOfflineTracks()
        .map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            tokenStore.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    runCatching { trackRepository.getMe() }.onSuccess { _user.value = it }
                } else {
                    _user.value = null
                }
            }
        }
    }

    fun playMix(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        playerController.playQueue(tracks, startIndex)
    }

    fun playFrom(tracks: List<Track>, trackId: Long) {
        val idx = tracks.indexOfFirst { it.id == trackId }
        if (idx < 0) return
        playerController.playQueue(tracks, idx)
    }
}
