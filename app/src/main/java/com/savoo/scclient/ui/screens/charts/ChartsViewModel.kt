package com.savoo.scclient.ui.screens.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChartsViewModel @Inject constructor(
    val playerController: PlayerController,
    private val trackRepository: TrackRepository,
) : ViewModel() {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError = _hasError.asStateFlow()

    init {
        viewModelScope.launch { loadCharts() }
    }

    private suspend fun loadCharts() {
        _isLoading.value = true
        _hasError.value = false
        runCatching { trackRepository.getCharts() }
            .onSuccess { _tracks.value = it }
            .onFailure {
                _tracks.value = emptyList()
                _hasError.value = true
            }
        _isLoading.value = false
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadCharts()
            _isRefreshing.value = false
        }
    }

    private fun queueTag() = "charts_trending_all-music"

    fun playFrom(trackId: Long) {
        val list = tracks.value
        val idx = list.indexOfFirst { it.id == trackId }
        if (idx < 0) return
        playerController.playQueue(list, idx, repeatAll = true, tag = queueTag())
    }

    fun playAll() {
        val list = tracks.value
        if (list.isEmpty()) return
        playerController.playQueue(list, 0, repeatAll = true, tag = queueTag())
    }
}
