package com.savoo.scclient.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.R
import com.savoo.scclient.data.model.LocalPlaylistSummary
import com.savoo.scclient.data.model.Playlist
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.displayArtworkUrl
import com.savoo.scclient.data.model.localPlaylistRouteId
import com.savoo.scclient.data.repository.PlaylistFullException
import com.savoo.scclient.data.repository.PlaylistsRepository
import com.savoo.scclient.ui.haptics.rememberHapticTick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val MAX_PLAYLIST_TITLE_CHARS = 255

sealed interface OnlinePlaylistsState {
    data object SignedOut : OnlinePlaylistsState
    data object Loading : OnlinePlaylistsState
    data object Failed : OnlinePlaylistsState
    data class Loaded(val playlists: List<Playlist>) : OnlinePlaylistsState
}

sealed interface PlaylistActionError {
    data object Full : PlaylistActionError
    data class Other(val message: String) : PlaylistActionError
}

data class PlaylistAddResult(val title: String, val added: Int, val queued: Boolean = false)

@HiltViewModel
class PlaylistActionsViewModel @Inject constructor(
    private val repository: PlaylistsRepository,
) : ViewModel() {

    val localPlaylists = repository.observeLocalPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoggedIn = repository.isLoggedIn

    private val _online = MutableStateFlow<OnlinePlaylistsState>(OnlinePlaylistsState.Loading)
    val online = _online.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<PlaylistActionError?>(null)
    val error = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun loadOnline() {
        if (!repository.isLoggedIn.value) {
            _online.value = OnlinePlaylistsState.SignedOut
            return
        }
        _online.value = OnlinePlaylistsState.Loading
        viewModelScope.launch {
            _online.value = runCatching { repository.onlinePlaylists() }
                .fold({ OnlinePlaylistsState.Loaded(it) }, { OnlinePlaylistsState.Failed })
        }
    }

    fun addToLocal(playlist: LocalPlaylistSummary, tracks: List<Track>, onDone: (PlaylistAddResult) -> Unit) {
        val routeId = localPlaylistRouteId(playlist.id)
        if (tracks.size > PlaylistsRepository.BULK_ADD_THRESHOLD) {
            repository.enqueueAdd(routeId, playlist.title, tracks)
            onDone(PlaylistAddResult(playlist.title, 0, queued = true))
            return
        }
        launchAction(onDone) { PlaylistAddResult(playlist.title, repository.addToLocal(playlist.id, tracks)) }
    }

    fun addToOnline(playlist: Playlist, tracks: List<Track>, onDone: (PlaylistAddResult) -> Unit) {
        if (tracks.size > PlaylistsRepository.BULK_ADD_THRESHOLD) {
            repository.enqueueAdd(playlist.id, playlist.title, tracks)
            onDone(PlaylistAddResult(playlist.title, 0, queued = true))
            return
        }
        launchAction(onDone) { PlaylistAddResult(playlist.title, repository.addToOnline(playlist.id, tracks.map { it.id })) }
    }

    fun create(title: String, online: Boolean, isPrivate: Boolean, tracks: List<Track>, onDone: (Long) -> Unit) =
        launchAction(onDone) {
            if (online) repository.createOnline(title, isPrivate, tracks).id
            else localPlaylistRouteId(repository.createLocal(title, tracks))
        }

    private fun <T> launchAction(onDone: (T) -> Unit, block: suspend () -> T) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                onDone(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlaylistFullException) {
                _error.value = PlaylistActionError.Full
            } catch (e: Exception) {
                _error.value = PlaylistActionError.Other(e.message ?: e.javaClass.simpleName)
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
fun playlistErrorText(error: PlaylistActionError): String = when (error) {
    PlaylistActionError.Full -> stringResource(R.string.playlist_error_full)
    is PlaylistActionError.Other -> stringResource(R.string.playlist_error_generic, error.message)
}

@Composable
fun PlaylistErrorCard(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddToPlaylistSheet(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    viewModel: PlaylistActionsViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = rememberHapticTick()
    val context = LocalContext.current
    val localPlaylists by viewModel.localPlaylists.collectAsState()
    val online by viewModel.online.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.clearError()
        viewModel.loadOnline()
    }

    val onAdded: (PlaylistAddResult) -> Unit = { result ->
        if (!result.queued) {
            val message = if (result.added > 0) R.string.playlist_added else R.string.playlist_already_there
            Toast.makeText(context, context.getString(message, result.title), Toast.LENGTH_SHORT).show()
        }
        onDismiss()
    }

    if (showCreate) {
        CreatePlaylistSheet(
            tracks = tracks,
            onDismiss = { showCreate = false },
            onCreated = { onDismiss() },
            viewModel = viewModel,
        )
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                stringResource(R.string.playlist_add_to),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (tracks.size > 1) {
                Text(
                    pluralStringResource(R.plurals.detail_tracks_count, tracks.size, tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(visible = busy) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp))
            }
            PlaylistErrorCard(
                text = error?.let { playlistErrorText(it) },
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                item(key = "new") {
                    PlaylistPickerRow(
                        title = stringResource(R.string.playlist_new),
                        subtitle = null,
                        enabled = !busy,
                        highlighted = true,
                        onClick = { haptic(); showCreate = true },
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        }
                    }
                }
                items(localPlaylists, key = { "local-${it.id}" }) { playlist ->
                    PlaylistPickerRow(
                        title = playlist.title,
                        subtitle = playlistSubtitle(stringResource(R.string.playlist_local_label), playlist.trackCount),
                        enabled = !busy,
                        onClick = { haptic(); viewModel.addToLocal(playlist, tracks, onAdded) },
                    ) {
                        TrackArtwork(
                            artworkUrl = playlist.artworkUrl,
                            contentDescription = null,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                when (val state = online) {
                    OnlinePlaylistsState.SignedOut -> item(key = "signed-out") {
                        PickerNote(stringResource(R.string.playlist_online_sign_in_hint))
                    }
                    OnlinePlaylistsState.Loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                    OnlinePlaylistsState.Failed -> item(key = "failed") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        ) {
                            Text(
                                stringResource(R.string.playlist_online_load_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.loadOnline() }) {
                                Text(stringResource(R.string.playlist_retry))
                            }
                        }
                    }
                    is OnlinePlaylistsState.Loaded -> items(state.playlists, key = { "online-${it.id}" }) { playlist ->
                        val label = listOfNotNull(
                            stringResource(R.string.playlist_online_label),
                            stringResource(R.string.playlist_private_label).takeIf { playlist.sharing == "private" },
                        ).joinToString(", ")
                        PlaylistPickerRow(
                            title = playlist.title,
                            subtitle = playlistSubtitle(label, playlist.trackCount),
                            enabled = !busy,
                            onClick = { haptic(); viewModel.addToOnline(playlist, tracks, onAdded) },
                        ) {
                            TrackArtwork(
                                artworkUrl = playlist.displayArtworkUrl,
                                contentDescription = null,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
private fun PlaylistPickerRow(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    leading: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPressed) 14.dp else 24.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pickerRowCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pickerRowScale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreatePlaylistSheet(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onCreated: (Long) -> Unit,
    viewModel: PlaylistActionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptic = rememberHapticTick()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var online by rememberSaveable { mutableStateOf(false) }
    var isPrivate by rememberSaveable { mutableStateOf(true) }
    val isBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !isBusy },
    )

    LaunchedEffect(Unit) { viewModel.clearError() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.playlist_new),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (tracks.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.detail_tracks_count, tracks.size, tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_PLAYLIST_TITLE_CHARS) },
                label = { Text(stringResource(R.string.playlist_title_hint)) },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PlaylistTypeRow(
                icon = Icons.Filled.Smartphone,
                title = stringResource(R.string.playlist_type_local_title),
                description = stringResource(R.string.playlist_type_local_desc),
                selected = !online,
                enabled = !busy,
                onClick = { haptic(); online = false },
            )
            Spacer(Modifier.height(6.dp))
            PlaylistTypeRow(
                icon = Icons.Filled.Cloud,
                title = stringResource(R.string.playlist_type_online_title),
                description = stringResource(
                    if (isLoggedIn) R.string.playlist_type_online_desc else R.string.playlist_type_online_signed_out
                ),
                selected = online,
                enabled = isLoggedIn && !busy,
                onClick = { haptic(); online = true },
            )
            AnimatedVisibility(
                visible = online,
                enter = fadeIn() + expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = !busy) { haptic(); isPrivate = !isPrivate }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.playlist_private_switch), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.playlist_private_switch_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { haptic(); isPrivate = it },
                        enabled = !busy,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            PlaylistErrorCard(
                text = error?.let { playlistErrorText(it) },
                modifier = Modifier.padding(top = 14.dp),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    haptic()
                    val name = title.trim()
                    viewModel.create(name, online, isPrivate, tracks) { routeId ->
                        Toast.makeText(context, context.getString(R.string.playlist_created, name), Toast.LENGTH_SHORT).show()
                        onDismiss()
                        onCreated(routeId)
                    }
                },
                enabled = title.isNotBlank() && !busy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (busy) {
                    LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.playlist_create_action), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PlaylistTypeRow(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPressed) 12.dp else if (selected) 28.dp else 18.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "playlistTypeCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "playlistTypeScale",
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "playlistTypeContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "playlistTypeContent",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled || selected) 1f else 0.6f }
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = content)
            Text(description, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.75f))
        }
        Spacer(Modifier.width(8.dp))
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)) + scaleOut(spring(stiffness = Spring.StiffnessMedium)),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RenamePlaylistSheet(
    currentTitle: String,
    busy: Boolean,
    error: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberHapticTick()
    var title by rememberSaveable { mutableStateOf(currentTitle) }
    val isBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !isBusy },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.playlist_rename),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_PLAYLIST_TITLE_CHARS) },
                label = { Text(stringResource(R.string.playlist_title_hint)) },
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            PlaylistErrorCard(
                text = error?.let { stringResource(R.string.playlist_error_generic, it) },
                modifier = Modifier.padding(top = 14.dp),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { haptic(); onSave(title.trim()) },
                enabled = title.isNotBlank() && title.trim() != currentTitle && !busy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (busy) {
                    LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.playlist_save_action), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
