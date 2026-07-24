package com.savoo.scclient.ui.screens.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savoo.scclient.BuildConfig
import com.savoo.scclient.R
import com.savoo.scclient.data.local.AppDatabase
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.player.OfflineTrackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val clientIdProvider: ClientIdProvider,
    private val favoritesDao: FavoritesDao,
    private val offlineTrackManager: OfflineTrackManager,
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _clientId = MutableStateFlow(clientIdProvider.cachedOrFallback())
    val clientId = _clientId.asStateFlow()

    private val _favoriteTracks = MutableStateFlow(0)
    val favoriteTracks = _favoriteTracks.asStateFlow()

    private val _favoriteArtists = MutableStateFlow(0)
    val favoriteArtists = _favoriteArtists.asStateFlow()

    private val _favoritePlaylists = MutableStateFlow(0)
    val favoritePlaylists = _favoritePlaylists.asStateFlow()

    private val _offlineTracks = MutableStateFlow(0)
    val offlineTracks = _offlineTracks.asStateFlow()

    init {
        refreshCounts()
    }

    fun refreshCounts() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _favoriteTracks.value = favoritesDao.getAllTracksSync().size
                _favoriteArtists.value = favoritesDao.getAllArtistsSync().size
                _favoritePlaylists.value = favoritesDao.getAllPlaylistsSync().size
                _offlineTracks.value = offlineTrackManager.getTrackCount()
            }
        }
    }

    fun refreshClientId(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { clientIdProvider.refresh() }
            _clientId.value = id
            onDone(id)
        }
    }

    fun setClientIdOverride(value: String) {
        clientIdProvider.setManualOverride(value)
        _clientId.value = value
    }

    fun resetDatabase(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.clearAllTables() }
            refreshCounts()
            onDone()
        }
    }

    fun buildDebugReport(): String = buildString {
        appendLine("Resona debug report")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
        appendLine("Application ID: ${BuildConfig.APPLICATION_ID}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("SC Client ID: ${_clientId.value}")
        appendLine("Favorites: ${_favoriteTracks.value} tracks, ${_favoriteArtists.value} artists, ${_favoritePlaylists.value} playlists")
        appendLine("Offline tracks: ${_offlineTracks.value}")
    }

    fun copyDebugReportToClipboard() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Resona debug report", buildDebugReport()))
    }
}

@Composable
private fun DebugSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.padding(16.dp), content = { content() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenuScreen(
    viewModel: DebugMenuViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val clientId by viewModel.clientId.collectAsState()
    val favoriteTracks by viewModel.favoriteTracks.collectAsState()
    val favoriteArtists by viewModel.favoriteArtists.collectAsState()
    val favoritePlaylists by viewModel.favoritePlaylists.collectAsState()
    val offlineTracks by viewModel.offlineTracks.collectAsState()
    var clientIdOverride by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_menu_title)) },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            DebugSectionCard(title = stringResource(R.string.debug_menu_build_info)) {
                Text("${stringResource(R.string.debug_menu_version)}: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                Text("${stringResource(R.string.debug_menu_build_type)}: ${BuildConfig.BUILD_TYPE}", style = MaterialTheme.typography.bodyMedium)
                Text("${stringResource(R.string.debug_menu_application_id)}: ${BuildConfig.APPLICATION_ID}", style = MaterialTheme.typography.bodyMedium)
                Text("${stringResource(R.string.debug_menu_device)}: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodyMedium)
            }

            DebugSectionCard(title = stringResource(R.string.debug_menu_client_id)) {
                Text(
                    stringResource(R.string.debug_menu_client_id_current, clientId),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    viewModel.refreshClientId {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.debug_menu_client_id_refreshed)) }
                    }
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.debug_menu_client_id_refresh))
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = clientIdOverride,
                        onValueChange = { clientIdOverride = it },
                        label = { Text(stringResource(R.string.debug_menu_client_id_override_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (clientIdOverride.isNotBlank()) {
                                viewModel.setClientIdOverride(clientIdOverride.trim())
                                clientIdOverride = ""
                            }
                        },
                        enabled = clientIdOverride.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.debug_menu_client_id_save))
                    }
                }
            }

            DebugSectionCard(title = stringResource(R.string.debug_menu_database)) {
                Text(stringResource(R.string.debug_menu_favorite_tracks, favoriteTracks), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.debug_menu_favorite_artists, favoriteArtists), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.debug_menu_favorite_playlists, favoritePlaylists), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.debug_menu_offline_tracks, offlineTracks), style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(stringResource(R.string.debug_menu_reset_database), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.debug_menu_reset_database_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable {
                            viewModel.resetDatabase {
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.debug_menu_reset_database_done)) }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.debug_menu_reset_database),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            DebugSectionCard(title = stringResource(R.string.debug_menu_actions)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.debug_menu_copy_report), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.debug_menu_copy_report_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        viewModel.copyDebugReportToClipboard()
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.debug_menu_report_copied)) }
                    }) {
                        Text(stringResource(R.string.debug_menu_copy))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.debug_menu_test_crash), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.debug_menu_test_crash_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { throw RuntimeException("Test crash triggered from Debug Menu") }) {
                        Text(stringResource(R.string.debug_menu_test_crash))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}
