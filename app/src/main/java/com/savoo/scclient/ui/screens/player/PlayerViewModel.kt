package com.savoo.scclient.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val controller: PlayerController,
    private val favoritesDao: FavoritesDao,
    private val offlineTrackManager: OfflineTrackManager,
) : ViewModel() {

    val isFavorite = controller.state.map { it.currentTrack?.id ?: 0L }
        .distinctUntilChanged()
        .flatMapLatest { id -> favoritesDao.isTrackFavorite(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isOffline = controller.state.map { it.currentTrack?.id ?: 0L }
        .distinctUntilChanged()
        .flatMapLatest { id -> offlineTrackManager.isOfflineTrack(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSavingOffline = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSavingOffline = _isSavingOffline

    fun toggleFavorite() {
        val track = controller.state.value.currentTrack ?: return
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

    fun saveForOffline() {
        val track = controller.state.value.currentTrack ?: return
        viewModelScope.launch {
            android.util.Log.d("OfflineTrack", "Starting save for: ${track.title} (media=${track.media != null})")
            _isSavingOffline.value = true
            val result = offlineTrackManager.saveForOffline(track)
            result.onSuccess { android.util.Log.d("OfflineTrack", "Save success") }
            result.onFailure { android.util.Log.e("OfflineTrack", "Save failed: ${it.message}") }
            _isSavingOffline.value = false
        }
    }

    fun removeFromOffline() {
        val track = controller.state.value.currentTrack ?: return
        viewModelScope.launch {
            offlineTrackManager.removeFromOffline(track.id)
        }
    }
}
