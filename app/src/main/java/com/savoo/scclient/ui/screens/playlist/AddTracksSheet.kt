package com.savoo.scclient.ui.screens.playlist

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.R
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.model.isLocalPlaylistRouteId
import com.savoo.scclient.data.model.toTrack
import com.savoo.scclient.data.repository.PlaylistFullException
import com.savoo.scclient.data.repository.PlaylistsRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.ui.components.PlaylistErrorCard
import com.savoo.scclient.ui.components.TrackArtwork
import com.savoo.scclient.ui.haptics.rememberHapticTick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class TrackPickSource { FAVORITES, OFFLINE, SEARCH }

data class TrackPickList(
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = true,
)

sealed interface TrackPickError {
    data object Full : TrackPickError
    data class Other(val message: String) : TrackPickError
}

data class TrackPickResult(val added: Int, val queued: Boolean)

private const val FAVORITES_PAGE_SIZE = 500
private const val SEARCH_DEBOUNCE_MS = 400L
private const val SEARCH_LIMIT = 50

@OptIn(FlowPreview::class)
@HiltViewModel
class AddTracksViewModel @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val offlineTrackManager: OfflineTrackManager,
    private val trackRepository: TrackRepository,
    private val playlistsRepository: PlaylistsRepository,
) : ViewModel() {

    private val _source = MutableStateFlow(TrackPickSource.FAVORITES)
    val source = _source.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val favorites = MutableStateFlow<List<Track>?>(null)
    private val offline = MutableStateFlow<List<Track>?>(null)
    private val searchResults = MutableStateFlow(TrackPickList(loading = false))

    private val _selected = MutableStateFlow<Map<Long, Track>>(emptyMap())
    val selected = _selected.asStateFlow()

    private val _existing = MutableStateFlow<Set<Long>>(emptySet())
    val existing = _existing.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<TrackPickError?>(null)
    val error = _error.asStateFlow()

    private var routeId = 0L
    private var started = false

    val list = combine(_source, _query, favorites, offline, searchResults) { source, query, favorites, offline, search ->
        val needle = query.trim()
        fun List<Track>.filtered() = if (needle.isEmpty()) this else filter {
            it.title.contains(needle, ignoreCase = true) || it.user.username.contains(needle, ignoreCase = true)
        }
        when (source) {
            TrackPickSource.FAVORITES -> TrackPickList(favorites.orEmpty().filtered(), loading = favorites == null)
            TrackPickSource.OFFLINE -> TrackPickList(offline.orEmpty().filtered(), loading = offline == null)
            TrackPickSource.SEARCH -> search
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackPickList())

    fun start(playlistRouteId: Long, existingIds: Set<Long>) {
        routeId = playlistRouteId
        _existing.value = existingIds
        _selected.value = emptyMap()
        _error.value = null
        if (started) return
        started = true
        val online = !isLocalPlaylistRouteId(playlistRouteId)
        viewModelScope.launch {
            favorites.value = withContext(Dispatchers.IO) {
                val all = ArrayList<Track>()
                var offset = 0
                while (true) {
                    val page = favoritesDao.getTracksPage(FAVORITES_PAGE_SIZE, offset)
                    page.mapTo(all) { it.toTrack() }
                    if (page.size < FAVORITES_PAGE_SIZE) break
                    offset += page.size
                }
                all
            }
        }
        viewModelScope.launch {
            offline.value = offlineTrackManager.getAllOfflineTracks().first()
                .filter { !online || it.sourceFolderUri == null }
                .map {
                    Track(
                        id = it.trackId,
                        title = it.title,
                        durationMs = it.durationMs,
                        artworkUrl = it.artworkUrl,
                        user = User(id = it.userId, username = it.username, avatarUrl = it.userAvatarUrl),
                        permalinkUrl = it.permalinkUrl,
                        genre = it.genre,
                    )
                }
        }
        viewModelScope.launch {
            combine(_source, _query) { source, query -> source to query.trim() }
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { (source, query) ->
                    if (source != TrackPickSource.SEARCH) return@collectLatest
                    if (query.length < 2) {
                        searchResults.value = TrackPickList(loading = false)
                        return@collectLatest
                    }
                    searchResults.update { it.copy(loading = true) }
                    val found = runCatching { trackRepository.searchTracks(query, SEARCH_LIMIT).items }.getOrDefault(emptyList())
                    searchResults.value = TrackPickList(found, loading = false)
                }
        }
    }

    fun setSource(source: TrackPickSource) {
        _source.value = source
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun toggle(track: Track) {
        if (track.id in _existing.value) return
        _selected.update { if (track.id in it) it - track.id else it + (track.id to track) }
    }

    fun selectAllVisible() {
        val existing = _existing.value
        _selected.update { current -> current + list.value.tracks.filter { it.id !in existing }.associateBy { it.id } }
    }

    fun clearSelection() {
        _selected.value = emptyMap()
    }

    fun confirm(title: String, onDone: (TrackPickResult) -> Unit) {
        val tracks = _selected.value.values.toList()
        if (tracks.isEmpty() || _busy.value) return
        if (tracks.size > PlaylistsRepository.BULK_ADD_THRESHOLD) {
            playlistsRepository.enqueueAdd(routeId, title, tracks)
            onDone(TrackPickResult(0, queued = true))
            return
        }
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val added = if (isLocalPlaylistRouteId(routeId)) {
                    playlistsRepository.addToLocal(-routeId, tracks)
                } else {
                    playlistsRepository.addToOnline(routeId, tracks.map { it.id })
                }
                onDone(TrackPickResult(added, queued = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlaylistFullException) {
                _error.value = TrackPickError.Full
            } catch (e: Exception) {
                _error.value = TrackPickError.Other(e.message ?: e.javaClass.simpleName)
            } finally {
                _busy.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddTracksSheet(
    playlistRouteId: Long,
    playlistTitle: String,
    existingIds: Set<Long>,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
    viewModel: AddTracksViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptic = rememberHapticTick()
    val source by viewModel.source.collectAsState()
    val query by viewModel.query.collectAsState()
    val list by viewModel.list.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val existing by viewModel.existing.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val isBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !isBusy },
    )

    LaunchedEffect(playlistRouteId) { viewModel.start(playlistRouteId, existingIds) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.playlist_add_tracks),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            ButtonGroup(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SourceButton(
                    label = stringResource(R.string.playlist_pick_favorites),
                    icon = Icons.Filled.Favorite,
                    checked = source == TrackPickSource.FAVORITES,
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    onClick = { haptic(); viewModel.setSource(TrackPickSource.FAVORITES) },
                    modifier = Modifier.weight(1f),
                )
                SourceButton(
                    label = stringResource(R.string.playlist_pick_offline),
                    icon = Icons.Filled.DownloadDone,
                    checked = source == TrackPickSource.OFFLINE,
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                    onClick = { haptic(); viewModel.setSource(TrackPickSource.OFFLINE) },
                    modifier = Modifier.weight(1f),
                )
                SourceButton(
                    label = stringResource(R.string.playlist_pick_search),
                    icon = Icons.Filled.Search,
                    checked = source == TrackPickSource.SEARCH,
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    onClick = { haptic(); viewModel.setSource(TrackPickSource.SEARCH) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = {
                    Text(
                        stringResource(
                            if (source == TrackPickSource.SEARCH) R.string.playlist_pick_search_hint else R.string.playlist_pick_filter_hint
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text(
                    stringResource(R.string.selection_count, selected.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { haptic(); viewModel.clearSelection() }) {
                        Text(stringResource(R.string.playlist_pick_clear))
                    }
                }
                if (list.tracks.isNotEmpty()) {
                    TextButton(onClick = { haptic(); viewModel.selectAllVisible() }) {
                        Text(stringResource(R.string.selection_select_all))
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    list.loading -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                    list.tracks.isEmpty() -> Text(
                        stringResource(
                            when {
                                source == TrackPickSource.SEARCH && query.trim().length < 2 -> R.string.playlist_pick_search_prompt
                                query.isNotBlank() -> R.string.search_nothing_found
                                else -> R.string.playlist_pick_empty
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(list.tracks, key = { it.id }) { track ->
                            PickTrackRow(
                                track = track,
                                selected = track.id in selected,
                                alreadyAdded = track.id in existing,
                                onClick = { haptic(); viewModel.toggle(track) },
                            )
                        }
                    }
                }
            }
            PlaylistErrorCard(
                text = when (val e = error) {
                    null -> null
                    TrackPickError.Full -> stringResource(R.string.playlist_error_full)
                    is TrackPickError.Other -> stringResource(R.string.playlist_error_generic, e.message)
                },
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    haptic()
                    viewModel.confirm(playlistTitle) { result ->
                        if (!result.queued) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    if (result.added > 0) R.string.playlist_added else R.string.playlist_already_there,
                                    playlistTitle,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                            onAdded()
                        }
                        onDismiss()
                    }
                },
                enabled = selected.isNotEmpty() && !busy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (busy) {
                    LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        if (selected.isEmpty()) stringResource(R.string.playlist_add_tracks)
                        else pluralStringResource(R.plurals.playlist_pick_add_count, selected.size, selected.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourceButton(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    shapes: androidx.compose.material3.ToggleButtonShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { if (it) onClick() },
        shapes = shapes,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PickTrackRow(
    track: Track,
    selected: Boolean,
    alreadyAdded: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPressed) 12.dp else if (selected) 26.dp else 18.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pickRowCorner",
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pickRowContainer",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (alreadyAdded) 0.5f else 1f }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interactionSource = interactionSource, indication = null, enabled = !alreadyAdded, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        TrackArtwork(
            artworkUrl = track.artworkUrl,
            contentDescription = null,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (alreadyAdded) stringResource(R.string.playlist_pick_in_playlist) else track.user.username,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) content.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(
                    if (selected || alreadyAdded) Modifier.background(MaterialTheme.colorScheme.primary)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ),
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selected || alreadyAdded,
                enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)),
                exit = fadeOut() + scaleOut(),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
