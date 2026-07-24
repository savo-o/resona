package com.savoo.scclient.ui.screens.importexport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.remote.ScProfile
import com.savoo.scclient.data.remote.SoundCloudImportRepository
import com.savoo.scclient.data.repository.FavoritesExporter
import com.savoo.scclient.data.repository.TelegramImportRepository
import com.savoo.scclient.telegram.ImportItemResult
import com.savoo.scclient.telegram.ImportItemStatus
import com.savoo.scclient.telegram.ImportProgress
import com.savoo.scclient.telegram.TelegramAuthState
import com.savoo.scclient.telegram.TelegramChatFolder
import com.savoo.scclient.telegram.TelegramChatList
import com.savoo.scclient.telegram.TelegramChatSummary
import com.savoo.scclient.telegram.TelegramClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val exporter: FavoritesExporter,
    private val scImportRepo: SoundCloudImportRepository,
    private val favoritesDao: FavoritesDao,
    private val telegramClient: TelegramClient,
    private val telegramImportRepo: TelegramImportRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _scState = MutableStateFlow<ScImportState>(ScImportState.Idle)
    val scState = _scState.asStateFlow()

    val telegramAvailable = telegramClient.isAvailable
    val telegramAuthState = telegramClient.authState
    val telegramChatFolders = telegramClient.chatFolders
    private val _telegramUiState = MutableStateFlow<TelegramImportUiState>(TelegramImportUiState.SignedOut)
    val telegramUiState = _telegramUiState.asStateFlow()

    init {
        // Chat avatars are fetched in the background (see TelegramClient.getChats) - patch them
        // into the currently shown chat list as they arrive instead of blocking the list on them.
        viewModelScope.launch {
            telegramClient.observeChatAvatarUpdates().collect { (chatId, avatarUrl) ->
                val current = _telegramUiState.value
                if (current is TelegramImportUiState.ChatPicker) {
                    _telegramUiState.value = current.copy(
                        chats = current.chats.map { if (it.chatId == chatId) it.copy(avatarUrl = avatarUrl) else it }
                    )
                }
            }
        }
    }

    fun telegramStart() {
        viewModelScope.launch { telegramClient.start() }
    }

    fun telegramSendPhone(phoneNumber: String) {
        viewModelScope.launch { runCatching { telegramClient.sendPhoneNumber(phoneNumber) } }
    }

    fun telegramSendCode(code: String) {
        viewModelScope.launch { runCatching { telegramClient.sendCode(code) } }
    }

    fun telegramSendPassword(password: String) {
        viewModelScope.launch { runCatching { telegramClient.sendPassword(password) } }
    }

    /** Step 1 of picking a chat: show "find a chat" - which folder (or all chats) to look in. */
    fun telegramLoadChats() {
        _telegramUiState.value = TelegramImportUiState.FolderPicker
    }

    fun telegramSelectFolder(chatList: TelegramChatList) {
        viewModelScope.launch {
            _telegramUiState.value = TelegramImportUiState.LoadingChats
            telegramClient.getChats(chatList).onSuccess { chats ->
                _telegramUiState.value = TelegramImportUiState.ChatPicker(chats)
            }.onFailure { e ->
                _telegramUiState.value = TelegramImportUiState.Error(e.message ?: "Failed to load chats")
            }
        }
    }

    fun telegramImportChat(chat: TelegramChatSummary) {
        // Show the loading state the instant the chat is tapped - the first real progress event
        // can take a moment (TDLib has to fetch chat history, then resolve/search the first track).
        _telegramUiState.value = TelegramImportUiState.Importing(
            chat,
            ImportProgress(totalScanned = 0, matched = 0, downloadedOffline = 0, failed = 0, skippedDuplicate = 0, currentTitle = null),
        )
        viewModelScope.launch {
            telegramImportRepo.importChat(chat.chatId).collect { progress ->
                _telegramUiState.value = TelegramImportUiState.Importing(chat, progress)
            }
            (_telegramUiState.value as? TelegramImportUiState.Importing)?.let {
                _telegramUiState.value = TelegramImportUiState.Summary(chat, it.progress)
            }
        }
    }

    fun telegramReset() {
        _telegramUiState.value = TelegramImportUiState.SignedOut
    }

    fun telegramLogOut() {
        viewModelScope.launch {
            telegramClient.logOut()
            _telegramUiState.value = TelegramImportUiState.SignedOut
        }
    }

    fun exportToFile(uri: Uri) {
        viewModelScope.launch {
            exporter.exportToFile(uri).onSuccess {
                _message.value = context.getString(R.string.msg_export_success)
            }.onFailure {
                _message.value = context.getString(R.string.msg_export_failed, it.message ?: "")
            }
        }
    }

    fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            exporter.importFromFile(uri).onSuccess { result ->
                _message.value = context.getString(R.string.msg_import_result, result.tracks, result.artists, result.playlists)
            }.onFailure {
                _message.value = context.getString(R.string.msg_import_failed, it.message ?: "")
            }
        }
    }

    fun scResolveProfile(input: String) {
        viewModelScope.launch {
            _scState.value = ScImportState.Resolving
            val result = scImportRepo.resolveProfile(input)
            result.onSuccess { profile ->
                _scState.value = ScImportState.ProfileFound(profile)
            }.onFailure { e ->
                if (input.startsWith("http") || input.contains("/")) {
                    _scState.value = ScImportState.Error(e.message ?: context.getString(R.string.msg_sc_resolve_failed))
                } else {
                    scSearchProfiles(input)
                }
            }
        }
    }

    fun scSearchProfiles(query: String) {
        viewModelScope.launch {
            _scState.value = ScImportState.Searching
            scImportRepo.searchProfiles(query).onSuccess { profiles ->
                if (profiles.size == 1) {
                    _scState.value = ScImportState.ProfileFound(profiles.first())
                } else if (profiles.isEmpty()) {
                    _scState.value = ScImportState.Error(context.getString(R.string.msg_sc_no_results))
                } else {
                    _scState.value = ScImportState.SearchResults(profiles, query)
                }
            }.onFailure { e ->
                _scState.value = ScImportState.Error(e.message ?: context.getString(R.string.msg_sc_search_failed))
            }
        }
    }

    fun scSelectProfile(profile: ScProfile) {
        _scState.value = ScImportState.ProfileFound(profile)
    }

    fun scFetchAndPreview(profile: ScProfile) {
        viewModelScope.launch {
            _scState.value = ScImportState.Fetching(progress = 0)
            scImportRepo.fetchLikedTracks(profile) { count ->
                _scState.value = ScImportState.Fetching(progress = count)
            }.onSuccess { result ->
                _scState.value = ScImportState.Preview(
                    profile = result.profile,
                    tracks = result.tracks,
                    playlists = result.playlists,
                )
            }.onFailure { e ->
                _scState.value = ScImportState.Error(e.message ?: context.getString(R.string.msg_sc_fetch_failed))
            }
        }
    }

    fun scImport(tracks: List<FavoriteTrack>, playlists: List<FavoritePlaylist> = emptyList(), replace: Boolean) {
        viewModelScope.launch {
            _scState.value = ScImportState.Importing
            try {
                if (replace) {
                    favoritesDao.getAllTracksSync().forEach { favoritesDao.removeTrack(it.trackId) }
                    favoritesDao.getAllPlaylistsSync().forEach { favoritesDao.removePlaylist(it.playlistId) }
                }
                tracks.forEach { favoritesDao.addTrack(it) }
                playlists.forEach { favoritesDao.addPlaylist(it) }
                _message.value = context.getString(R.string.msg_sc_import_result, tracks.size)
                _scState.value = ScImportState.Idle
            } catch (e: Exception) {
                _scState.value = ScImportState.Error(e.message ?: context.getString(R.string.msg_sc_import_failed))
            }
        }
    }

    fun scReset() {
        _scState.value = ScImportState.Idle
    }

    fun clearMessage() {
        _message.value = null
    }
}

sealed class ScImportState {
    data object Idle : ScImportState()
    data object Resolving : ScImportState()
    data class ProfileFound(val profile: ScProfile) : ScImportState()
    data class Fetching(val progress: Int, val isPlaylists: Boolean = false) : ScImportState()
    data class Preview(
        val profile: ScProfile,
        val tracks: List<FavoriteTrack>,
        val playlists: List<FavoritePlaylist> = emptyList(),
    ) : ScImportState()
    data object Importing : ScImportState()
    data class Error(val message: String) : ScImportState()
    data class SearchResults(val profiles: List<ScProfile>, val query: String) : ScImportState()
    data object Searching : ScImportState()
}

/** BETA: local UI state for the Telegram import flow, layered on top of [TelegramAuthState]. */
sealed class TelegramImportUiState {
    data object SignedOut : TelegramImportUiState()
    data object FolderPicker : TelegramImportUiState()
    data object LoadingChats : TelegramImportUiState()
    data class ChatPicker(val chats: List<TelegramChatSummary>) : TelegramImportUiState()
    data class Importing(val chat: TelegramChatSummary, val progress: ImportProgress) : TelegramImportUiState()
    data class Summary(val chat: TelegramChatSummary, val progress: ImportProgress) : TelegramImportUiState()
    data class Error(val message: String) : TelegramImportUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    onBack: () -> Unit = {},
    viewModel: ImportExportViewModel = hiltViewModel(),
) {
    val message by viewModel.message.collectAsState()
    val scState by viewModel.scState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var scInput by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportToFile(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFromFile(it) } }

    message?.let { msg ->
        scope.launch {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ImportExportSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportLauncher.launch("resona_favorites.json") }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.export_favorites), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.export_favorites_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ImportExportDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { importLauncher.launch(arrayOf("application/json")) }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.import_favorites), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.import_favorites_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ImportExportSectionCard {
                SoundCloudImportSection(
                    scState = scState,
                    scInput = scInput,
                    onInputChange = { scInput = it },
                    onResolve = { viewModel.scResolveProfile(scInput) },
                    onFetchAndPreview = { viewModel.scFetchAndPreview(it) },
                    onResolveProfile = { viewModel.scSelectProfile(it) },
                    onImport = { tracks, playlists, replace -> viewModel.scImport(tracks, playlists, replace) },
                    onReset = { scInput = ""; viewModel.scReset() },
                    onErrorDismiss = { viewModel.scReset() },
                )
            }

            ImportExportSectionCard {
                TelegramImportSection(
                    available = viewModel.telegramAvailable,
                    authState = viewModel.telegramAuthState.collectAsState().value,
                    uiState = viewModel.telegramUiState.collectAsState().value,
                    folders = viewModel.telegramChatFolders.collectAsState().value,
                    onStart = { viewModel.telegramStart() },
                    onSendPhone = { viewModel.telegramSendPhone(it) },
                    onSendCode = { viewModel.telegramSendCode(it) },
                    onSendPassword = { viewModel.telegramSendPassword(it) },
                    onLoadChats = { viewModel.telegramLoadChats() },
                    onSelectFolder = { viewModel.telegramSelectFolder(it) },
                    onImportChat = { viewModel.telegramImportChat(it) },
                    onReset = { viewModel.telegramReset() },
                    onLogOut = { viewModel.telegramLogOut() },
                )
            }
        }
    }
}

@Composable
private fun ImportExportSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(content = content)
    }
}

@Composable
private fun ImportExportDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SoundCloudImportSection(
    scState: ScImportState,
    scInput: String,
    onInputChange: (String) -> Unit,
    onResolve: () -> Unit,
    onFetchAndPreview: (com.savoo.scclient.data.remote.ScProfile) -> Unit,
    onResolveProfile: (com.savoo.scclient.data.remote.ScProfile) -> Unit,
    onImport: (List<FavoriteTrack>, List<FavoritePlaylist>, Boolean) -> Unit,
    onReset: () -> Unit,
    onErrorDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.import_sc_title), style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.import_sc_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        when (scState) {
            is ScImportState.Idle -> {
                OutlinedTextField(
                    value = scInput,
                    onValueChange = onInputChange,
                    placeholder = { Text(stringResource(R.string.import_sc_hint)) },
                    singleLine = true,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onResolve,
                    enabled = scInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_sc_resolve))
                }
            }

            is ScImportState.Resolving -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.import_sc_resolving), style = MaterialTheme.typography.bodyMedium)
                }
            }

            is ScImportState.Searching -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.import_sc_searching), style = MaterialTheme.typography.bodyMedium)
                }
            }

            is ScImportState.SearchResults -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    Text(
                        stringResource(R.string.import_sc_select_profile),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${scState.profiles.size} results for \"${scState.query}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    scState.profiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onResolveProfile(profile) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = profile.avatarUrl?.replace("-large", "-t500x500"),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    profile.fullName ?: profile.username,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "@${profile.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_sc_cancel))
                    }
                }
            }

            is ScImportState.ProfileFound -> {
                val profile = scState.profile
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    Text(profile.username, style = MaterialTheme.typography.titleMedium)
                    profile.fullName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        TextButton(onClick = onReset) { Text(stringResource(R.string.import_sc_cancel)) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onFetchAndPreview(profile) }) { Text(stringResource(R.string.import_sc_fetch)) }
                    }
                }
            }

            is ScImportState.Fetching -> {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp))
                    if (scState.isPlaylists) {
                        Text(stringResource(R.string.import_sc_fetching_playlists, scState.progress), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(R.string.import_sc_fetched, scState.progress), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.import_sc_slow),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is ScImportState.Preview -> {
                val preview = scState
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.import_sc_preview), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.import_sc_preview_count, preview.tracks.size, preview.profile.username),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (preview.playlists.isNotEmpty()) {
                        Text(
                            stringResource(R.string.import_sc_preview_playlists, preview.playlists.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { onImport(preview.tracks, preview.playlists, false) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.import_sc_merge)) }
                        Button(
                            onClick = { onImport(preview.tracks, preview.playlists, true) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.import_sc_replace)) }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_sc_cancel)) }
                }
            }

            is ScImportState.Importing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.import_sc_importing), style = MaterialTheme.typography.bodyMedium)
                }
            }

            is ScImportState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        scState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onErrorDismiss) { Text(stringResource(R.string.import_sc_dismiss)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TelegramImportSection(
    available: Boolean,
    authState: TelegramAuthState,
    uiState: TelegramImportUiState,
    folders: List<TelegramChatFolder>,
    onStart: () -> Unit,
    onSendPhone: (String) -> Unit,
    onSendCode: (String) -> Unit,
    onSendPassword: (String) -> Unit,
    onLoadChats: () -> Unit,
    onSelectFolder: (TelegramChatList) -> Unit,
    onImportChat: (TelegramChatSummary) -> Unit,
    onReset: () -> Unit,
    onLogOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.import_tg_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.import_tg_beta),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.import_tg_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        if (!available) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.import_tg_unavailable), style = MaterialTheme.typography.bodyMedium)
            }
            return
        }

        when (authState) {
            is TelegramAuthState.Unavailable -> Text(
                stringResource(R.string.import_tg_unavailable),
                style = MaterialTheme.typography.bodyMedium,
            )

            is TelegramAuthState.LoggedOut -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_tg_start))
            }

            is TelegramAuthState.Connecting -> LoadingRow(stringResource(R.string.import_tg_connecting))

            is TelegramAuthState.WaitPhoneNumber -> {
                var phone by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    placeholder = { Text(stringResource(R.string.import_tg_phone_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onSendPhone(phone) }, enabled = phone.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_tg_send_phone))
                }
            }

            is TelegramAuthState.WaitCode -> {
                var code by remember { mutableStateOf("") }
                Text(
                    stringResource(R.string.import_tg_code_sent, authState.phoneNumber),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text(stringResource(R.string.import_tg_code_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onSendCode(code) }, enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_tg_send_code))
                }
            }

            is TelegramAuthState.WaitPassword -> {
                var password by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text(authState.passwordHint ?: stringResource(R.string.import_tg_password_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onSendPassword(password) }, enabled = password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_tg_send_password))
                }
            }

            is TelegramAuthState.Error -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp)
            ) {
                Text(authState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onStart) { Text(stringResource(R.string.import_sc_dismiss)) }
            }

            is TelegramAuthState.Ready -> when (uiState) {
                is TelegramImportUiState.SignedOut -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onLoadChats) { Text(stringResource(R.string.import_tg_load_chats)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onLogOut) { Text(stringResource(R.string.import_tg_log_out)) }
                }

                is TelegramImportUiState.FolderPicker -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.import_tg_select_folder), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectFolder(TelegramChatList.Main) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.import_tg_all_chats), style = MaterialTheme.typography.bodyLarge)
                    }
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectFolder(TelegramChatList.Folder(folder.id)) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                is TelegramImportUiState.LoadingChats -> LoadingRow(stringResource(R.string.import_tg_loading_chats))

                is TelegramImportUiState.ChatPicker -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.import_tg_select_chat), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    // TDLib already returns chats pre-sorted (pinned first) for the requested list -
                    // this sortedByDescending is just a defensive guarantee, not a re-derivation.
                    uiState.chats.sortedByDescending { it.isPinned }.forEach { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onImportChat(chat) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = chat.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(chat.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (chat.isPinned) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = stringResource(R.string.import_tg_pinned),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                is TelegramImportUiState.Importing -> {
                    val progress = uiState.progress
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.import_tg_progress, progress.processed, progress.currentTitle ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.import_tg_progress_stats, progress.matched, progress.downloadedOffline, progress.failed, progress.skippedDuplicate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (progress.recentResults.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(8.dp),
                            ) {
                                progress.recentResults.forEach { item -> ImportResultRow(item) }
                            }
                        }
                    }
                }

                is TelegramImportUiState.Summary -> {
                    val progress = uiState.progress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.import_tg_done), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.import_tg_progress_stats, progress.matched, progress.downloadedOffline, progress.failed, progress.skippedDuplicate),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.import_sc_dismiss))
                        }
                    }
                }

                is TelegramImportUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp)
                ) {
                    Text(uiState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReset) { Text(stringResource(R.string.import_sc_dismiss)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        LoadingIndicator(modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImportResultRow(item: ImportItemResult) {
    val (icon, tint) = when (item.status) {
        ImportItemStatus.MATCHED -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        ImportItemStatus.DOWNLOADED_OFFLINE -> Icons.Filled.CloudDone to MaterialTheme.colorScheme.tertiary
        ImportItemStatus.FAILED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        ImportItemStatus.SKIPPED_DUPLICATE -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = item.status.name, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            item.resolvedTitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
