package com.savoo.scclient.ui.screens.importexport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.remote.ScProfile
import com.savoo.scclient.data.remote.SoundCloudImportRepository
import com.savoo.scclient.data.repository.FavoritesExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val exporter: FavoritesExporter,
    private val scImportRepo: SoundCloudImportRepository,
    private val favoritesDao: FavoritesDao,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _scState = MutableStateFlow<ScImportState>(ScImportState.Idle)
    val scState = _scState.asStateFlow()

    fun exportToFile(uri: Uri) {
        viewModelScope.launch {
            exporter.exportToFile(uri).onSuccess {
                _message.value = "Favorites exported successfully"
            }.onFailure {
                _message.value = "Export failed: ${it.message}"
            }
        }
    }

    fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            exporter.importFromFile(uri).onSuccess { result ->
                _message.value = "Imported: ${result.tracks} tracks, ${result.artists} artists, ${result.playlists} playlists"
            }.onFailure {
                _message.value = "Import failed: ${it.message}"
            }
        }
    }

    fun scResolveProfile(input: String) {
        viewModelScope.launch {
            _scState.value = ScImportState.Resolving
            scImportRepo.resolveProfile(input).onSuccess { profile ->
                _scState.value = ScImportState.ProfileFound(profile)
            }.onFailure { e ->
                _scState.value = ScImportState.Error(e.message ?: "Failed to resolve profile")
            }
        }
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
                )
            }.onFailure { e ->
                _scState.value = ScImportState.Error(e.message ?: "Failed to fetch likes")
            }
        }
    }

    fun scImport(tracks: List<FavoriteTrack>, replace: Boolean) {
        viewModelScope.launch {
            _scState.value = ScImportState.Importing
            try {
                if (replace) {
                    favoritesDao.getAllTracksSync().forEach { favoritesDao.removeTrack(it.trackId) }
                }
                tracks.forEach { favoritesDao.addTrack(it) }
                _message.value = "Imported ${tracks.size} tracks from SoundCloud"
                _scState.value = ScImportState.Idle
            } catch (e: Exception) {
                _scState.value = ScImportState.Error(e.message ?: "Import failed")
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
    data class Fetching(val progress: Int) : ScImportState()
    data class Preview(
        val profile: ScProfile,
        val tracks: List<FavoriteTrack>,
    ) : ScImportState()
    data object Importing : ScImportState()
    data class Error(val message: String) : ScImportState()
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
                title = { Text("Import / Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        ) {
            RoundedDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { exportLauncher.launch("resona_favorites.json") }
                    .padding(horizontal = 24.dp, vertical = 20.dp),
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
                    Text("Export Favorites", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Save tracks, artists and playlists to a JSON file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            RoundedDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { importLauncher.launch(arrayOf("application/json")) }
                    .padding(horizontal = 24.dp, vertical = 20.dp),
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
                    Text("Import Favorites", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Load favorites from a JSON file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            RoundedDivider()

            SoundCloudImportSection(
                scState = scState,
                scInput = scInput,
                onInputChange = { scInput = it },
                onResolve = { viewModel.scResolveProfile(scInput) },
                onFetchAndPreview = { viewModel.scFetchAndPreview(it) },
                onImport = { tracks, replace -> viewModel.scImport(tracks, replace) },
                onReset = { scInput = ""; viewModel.scReset() },
                onErrorDismiss = { viewModel.scReset() },
            )

            RoundedDivider()
        }
    }
}

@Composable
private fun SoundCloudImportSection(
    scState: ScImportState,
    scInput: String,
    onInputChange: (String) -> Unit,
    onResolve: () -> Unit,
    onFetchAndPreview: (com.savoo.scclient.data.remote.ScProfile) -> Unit,
    onImport: (List<FavoriteTrack>, Boolean) -> Unit,
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
            Text("Import from SoundCloud", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Enter a SoundCloud username or profile URL",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        when (scState) {
            is ScImportState.Idle -> {
                OutlinedTextField(
                    value = scInput,
                    onValueChange = onInputChange,
                    placeholder = { Text("username or soundcloud.com/username") },
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

                TextButton(
                    onClick = onResolve,
                    enabled = scInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Resolve Profile")
                }
            }

            is ScImportState.Resolving -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Resolving profile...", style = MaterialTheme.typography.bodyMedium)
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
                        TextButton(onClick = onReset) { Text("Cancel") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onFetchAndPreview(profile) }) { Text("Fetch Likes") }
                    }
                }
            }

            is ScImportState.Fetching -> {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Fetched ${scState.progress} tracks...", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This may take a while for profiles with many likes",
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
                    Text("Preview", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${preview.tracks.size} liked tracks from ${preview.profile.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { onImport(preview.tracks, true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Replace current favorites") }
                    TextButton(
                        onClick = { onImport(preview.tracks, false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Merge with current favorites") }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }

            is ScImportState.Importing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Importing...", style = MaterialTheme.typography.bodyMedium)
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
                    TextButton(onClick = onErrorDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun RoundedDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}
