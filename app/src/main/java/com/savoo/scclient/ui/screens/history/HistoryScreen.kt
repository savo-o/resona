package com.savoo.scclient.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.local.MIN_COUNTED_MS
import com.savoo.scclient.data.local.PlayHistoryDao
import com.savoo.scclient.data.model.PlayEvent
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.components.EmptyState
import com.savoo.scclient.ui.components.UndoAction
import com.savoo.scclient.ui.components.UndoController
import com.savoo.scclient.ui.haptics.rememberHapticTick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val HISTORY_EVENT_LIMIT = 1000

data class HistoryEntry(
    val eventIds: List<Long>,
    val trackId: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val artworkUrl: String?,
    val playedAt: Long,
    val msPlayed: Long,
    val playCount: Int,
)

data class HistoryDay(val dayStart: Long, val entries: List<HistoryEntry>)

@UnstableApi
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
    private val trackRepository: TrackRepository,
    private val offlineTrackManager: OfflineTrackManager,
    val playerController: PlayerController,
    private val undoController: UndoController,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _resolvingTrackId = MutableStateFlow<Long?>(null)
    val resolvingTrackId = _resolvingTrackId.asStateFlow()

    private val events = playHistoryDao.observeRecentEvents(HISTORY_EVENT_LIMIT)

    val days = combine(events, _query) { list, query ->
        val needle = query.trim().lowercase()
        val filtered = if (needle.isEmpty()) list else list.filter {
            it.title.lowercase().contains(needle) || it.artistName.lowercase().contains(needle)
        }
        groupByDay(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isEmpty = events
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun play(entry: HistoryEntry) {
        if (_resolvingTrackId.value != null) return
        viewModelScope.launch {
            _resolvingTrackId.value = entry.trackId
            val track = resolveTrack(entry)
            _resolvingTrackId.value = null
            if (track != null) playerController.playQueue(listOf(track), 0, tag = "history")
        }
    }

    private suspend fun resolveTrack(entry: HistoryEntry): Track? {
        offlineTrackManager.getOfflineTrack(entry.trackId)?.let {
            return Track(
                id = it.trackId,
                title = it.title,
                durationMs = it.durationMs,
                artworkUrl = it.artworkUrl,
                user = User(id = it.userId, username = it.username, avatarUrl = it.userAvatarUrl),
                permalinkUrl = it.permalinkUrl,
                genre = it.genre,
            )
        }
        return runCatching { trackRepository.getTrack(entry.trackId) }.getOrNull()
    }

    fun remove(entry: HistoryEntry) {
        viewModelScope.launch {
            val dao = playHistoryDao
            val removed = dao.eventsByIds(entry.eventIds)
            entry.eventIds.forEach { dao.deleteEvent(it) }
            if (removed.isEmpty()) return@launch
            undoController.show(
                UndoAction(
                    messageRes = R.string.history_entry_removed,
                    icon = Icons.Filled.History,
                    onUndo = { runCatching { dao.insertAll(removed) } },
                )
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch { playHistoryDao.clearHistory() }
    }
}

private fun groupByDay(events: List<PlayEvent>): List<HistoryDay> {
    val calendar = Calendar.getInstance()
    return events
        .groupBy { event ->
            calendar.timeInMillis = event.playedAt
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }
        .map { (dayStart, dayEvents) -> HistoryDay(dayStart, mergeAdjacent(dayEvents)) }
        .sortedByDescending { it.dayStart }
}

private fun mergeAdjacent(events: List<PlayEvent>): List<HistoryEntry> {
    val result = mutableListOf<HistoryEntry>()
    events.sortedByDescending { it.playedAt }.forEach { event ->
        val last = result.lastOrNull()
        if (last != null && last.trackId == event.trackId) {
            result[result.lastIndex] = last.copy(
                eventIds = last.eventIds + event.id,
                msPlayed = last.msPlayed + event.msPlayed,
                playCount = last.playCount + 1,
                artworkUrl = last.artworkUrl ?: event.artworkUrl,
            )
        } else {
            result.add(
                HistoryEntry(
                    eventIds = listOf(event.id),
                    trackId = event.trackId,
                    title = event.title,
                    artistId = event.artistId,
                    artistName = event.artistName,
                    artworkUrl = event.artworkUrl,
                    playedAt = event.playedAt,
                    msPlayed = event.msPlayed,
                    playCount = 1,
                )
            )
        }
    }
    return result
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val haptic = rememberHapticTick()
    val days by viewModel.days.collectAsState()
    val query by viewModel.query.collectAsState()
    val isEmpty by viewModel.isEmpty.collectAsState()
    val resolvingTrackId by viewModel.resolvingTrackId.collectAsState()

    val playerState by viewModel.playerController.state.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isEmpty) {
                        IconButton(onClick = { haptic(); showClearDialog = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (!isEmpty) {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (days.isEmpty()) {
                EmptyState(
                    icon = if (isEmpty) Icons.Filled.History else Icons.Filled.Search,
                    text = if (isEmpty) stringResource(R.string.history_empty)
                        else stringResource(R.string.search_nothing_found),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    days.forEach { day ->
                        item(key = "header_${day.dayStart}") {
                            Text(
                                text = formatDayLabel(day.dayStart),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                            )
                        }
                        items(day.entries, key = { it.eventIds.first() }) { entry ->
                            HistoryRow(
                                entry = entry,
                                isPlaying = playerState.currentTrack?.id == entry.trackId && playerState.isPlaying,
                                isLoading = resolvingTrackId == entry.trackId || playerState.loadingTrackId == entry.trackId,
                                onClick = { haptic(); viewModel.play(entry) },
                                onRemove = { haptic(); viewModel.remove(entry) },
                                modifier = Modifier.padding(bottom = 8.dp).animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                    Text(stringResource(R.string.history_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val artwork = entry.artworkUrl
                if (artwork != null) {
                    AsyncImage(
                        model = artwork.replace("-large", "-t500x500"),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (isLoading) {
                    LoadingIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    historySubtitle(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.history_remove_entry),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun historySubtitle(entry: HistoryEntry): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.playedAt))
    val listened = formatListenDuration(entry.msPlayed)
    return when {
        entry.playCount > 1 -> stringResource(R.string.history_entry_repeated_format, time, listened, entry.playCount)
        entry.msPlayed < MIN_COUNTED_MS -> time
        else -> stringResource(R.string.history_entry_format, time, listened)
    }
}

private fun formatListenDuration(ms: Long): String {
    val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

@Composable
private fun formatDayLabel(dayStart: Long): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterday = today - TimeUnit.DAYS.toMillis(1)
    return when (dayStart) {
        today -> stringResource(R.string.history_today)
        yesterday -> stringResource(R.string.history_yesterday)
        else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(dayStart))
    }
}
