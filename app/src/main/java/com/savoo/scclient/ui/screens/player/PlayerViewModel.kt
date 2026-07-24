package com.savoo.scclient.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.LyricsLine
import com.savoo.scclient.data.repository.FavoritesRepository
import com.savoo.scclient.data.repository.LyricsRepository
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val controller: PlayerController,
    private val favoritesDao: FavoritesDao,
    private val favoritesRepository: FavoritesRepository,
    private val offlineTrackManager: OfflineTrackManager,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val isFavorite = controller.state.map { it.currentTrack?.id ?: 0L }
        .distinctUntilChanged()
        .flatMapLatest { id -> favoritesDao.isTrackFavorite(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isOffline = controller.state.map { it.currentTrack?.id ?: 0L }
        .distinctUntilChanged()
        .flatMapLatest { id -> offlineTrackManager.isOfflineTrack(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lyrics = controller.state.map { it.currentTrack }
        .distinctUntilChanged { old, new -> old?.id == new?.id }
        .flatMapLatest { track ->
            flow {
                emit(null)
                if (track != null) emit(lyricsRepository.getSyncedLyrics(track))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lyricsOffsetMs = settingsRepository.settings.map { it.lyricsOffsetMs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val activeLyricsLine = combine(controller.state, lyrics, lyricsOffsetMs) { state, lines, offsetMs ->
        if (lines.isNullOrEmpty()) -1
        else lines.indexOfLast { it.timeMs <= state.positionMs + 200 + offsetMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    fun adjustLyricsOffset(deltaMs: Long) {
        viewModelScope.launch {
            settingsRepository.setLyricsOffsetMs(lyricsOffsetMs.value + deltaMs)
        }
    }

    private val _isSavingOffline = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSavingOffline = _isSavingOffline

    fun toggleFavorite() {
        val track = controller.state.value.currentTrack ?: return
        viewModelScope.launch {
            favoritesRepository.toggleTrackFavorite(track)
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
