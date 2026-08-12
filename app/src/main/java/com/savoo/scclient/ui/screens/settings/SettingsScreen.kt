package com.savoo.scclient.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.savoo.scclient.R
import com.savoo.scclient.data.model.UpdateChannel
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.data.repository.AppIconManager
import com.savoo.scclient.data.repository.AppIconOption
import com.savoo.scclient.data.repository.AppSettings
import com.savoo.scclient.data.repository.DarkModeOption
import com.savoo.scclient.data.repository.HapticsIntensity
import com.savoo.scclient.data.repository.LanguageOption
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.UpdateCheckResult
import com.savoo.scclient.data.repository.UpdateRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.ui.components.SwitchItem
import com.savoo.scclient.ui.haptics.rememberHapticTick
import com.savoo.scclient.ui.theme.AppColorTheme
import com.savoo.scclient.BuildConfig
import androidx.compose.material.icons.filled.SystemUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface UpdateCheckUiState {
    data object Idle : UpdateCheckUiState
    data object Checking : UpdateCheckUiState
    data class Available(val result: UpdateCheckResult.Available) : UpdateCheckUiState
    data object UpToDate : UpdateCheckUiState
    data object Error : UpdateCheckUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val offlineTrackManager: OfflineTrackManager,
    private val clientIdProvider: ClientIdProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val autoplayNext = repository.autoplayNext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _updateCheckState = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val updateCheckState = _updateCheckState.asStateFlow()

    private val _cacheSize = MutableStateFlow("Calculating...")
    val cacheSize = _cacheSize.asStateFlow()

    private val _offlineSize = MutableStateFlow("Calculating...")
    val offlineSize = _offlineSize.asStateFlow()

    private val _offlineCount = MutableStateFlow(0)
    val offlineCount = _offlineCount.asStateFlow()

    private val _isRefreshingClientId = MutableStateFlow(false)
    val isRefreshingClientId = _isRefreshingClientId.asStateFlow()

    init {
        calculateCacheSize()
        calculateOfflineSize()
    }

    fun setColorTheme(theme: AppColorTheme) = viewModelScope.launch { repository.setColorTheme(theme) }
    fun setDarkMode(mode: DarkModeOption) = viewModelScope.launch { repository.setDarkMode(mode) }
    fun setAutoplayNext(value: Boolean) = viewModelScope.launch { repository.setAutoplayNext(value) }
    fun setDynamicFromTrack(value: Boolean) = viewModelScope.launch { repository.setDynamicFromTrack(value) }
    fun setDeveloperMode(value: Boolean) = viewModelScope.launch { repository.setDeveloperMode(value) }
    fun setHapticsEnabled(value: Boolean) = viewModelScope.launch { repository.setHapticsEnabled(value) }
    fun setHapticsIntensity(value: HapticsIntensity) = viewModelScope.launch { repository.setHapticsIntensity(value) }
    fun setUpdateChannel(channel: UpdateChannel) = viewModelScope.launch { repository.setUpdateChannel(channel) }
    fun setAutoCheckUpdates(value: Boolean) = viewModelScope.launch { repository.setAutoCheckUpdates(value) }
    fun setAppIcon(option: AppIconOption) = viewModelScope.launch {
        repository.setAppIcon(option)
        AppIconManager.apply(context, option)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckUiState.Checking
            val channel = settings.value.updateChannel
            _updateCheckState.value = when (val result = updateRepository.checkForUpdate(channel)) {
                is UpdateCheckResult.Available -> UpdateCheckUiState.Available(result)
                UpdateCheckResult.UpToDate -> UpdateCheckUiState.UpToDate
                UpdateCheckResult.Error -> UpdateCheckUiState.Error
            }
        }
    }

    fun consumeUpdateCheckState() {
        _updateCheckState.value = UpdateCheckUiState.Idle
    }
    fun setLanguage(language: LanguageOption) = viewModelScope.launch {
        repository.setLanguage(language)
        context.getSharedPreferences("sc_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("language", language.name).commit()
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                var total = 0L
                total += getDirSize(context.cacheDir)
                total += getDirSize(context.codeCacheDir)
                formatSize(total)
            }
            _cacheSize.value = size
        }
    }

    fun clearCache(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                deleteDir(context.cacheDir)
                deleteDir(context.codeCacheDir)
            }
            calculateCacheSize()
            onDone()
        }
    }

    fun calculateOfflineSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                formatSize(offlineTrackManager.getTotalSize())
            }
            _offlineSize.value = size
            _offlineCount.value = offlineTrackManager.getTrackCount()
        }
    }

    fun clearOffline(onDone: () -> Unit) {
        viewModelScope.launch {
            offlineTrackManager.clearAllOffline()
            calculateOfflineSize()
            onDone()
        }
    }

    fun refreshClientId(onDone: () -> Unit) {
        viewModelScope.launch {
            _isRefreshingClientId.value = true
            withContext(Dispatchers.IO) { clientIdProvider.refresh() }
            _isRefreshingClientId.value = false
            onDone()
        }
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        return dir.delete()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun SettingsSectionCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorThemeSwatch(
    theme: AppColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(theme.seedPrimary)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            theme.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenDebugMenu: () -> Unit = {},
    onOpenDislikedArtists: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val autoplay by viewModel.autoplayNext.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val offlineCount by viewModel.offlineCount.collectAsState()
    val offlineSize by viewModel.offlineSize.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val isRefreshingClientId by viewModel.isRefreshingClientId.collectAsState()
    var showAbout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = rememberHapticTick()

    LaunchedEffect(updateCheckState) {
        when (val state = updateCheckState) {
            UpdateCheckUiState.UpToDate -> {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_update_up_to_date))
                viewModel.consumeUpdateCheckState()
            }
            UpdateCheckUiState.Error -> {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_update_check_failed))
                viewModel.consumeUpdateCheckState()
            }
            is UpdateCheckUiState.Available -> Unit // rendered as a dialog below, dismissed explicitly
            UpdateCheckUiState.Checking, UpdateCheckUiState.Idle -> Unit
        }
    }

    (updateCheckState as? UpdateCheckUiState.Available)?.let { state ->
        UpdateAvailableDialog(
            result = state.result,
            onDismiss = { viewModel.consumeUpdateCheckState() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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

            SettingsSectionCard(title = stringResource(R.string.settings_color_theme)) {
                FlowRow(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppColorTheme.entries.filter { it != AppColorTheme.DYNAMIC_TRACK }.forEach { theme ->
                        ColorThemeSwatch(
                            theme = theme,
                            selected = settings.colorTheme == theme,
                            onClick = { viewModel.setColorTheme(theme) },
                        )
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_app_icon)) {
                val iconOptions = AppIconOption.entries
                val iconLabelResIds = listOf(R.string.settings_app_icon_normal, R.string.settings_app_icon_dynamic)
                ButtonGroup(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    iconOptions.forEachIndexed { index, option ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            iconOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = settings.appIcon == option,
                            onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setAppIcon(option) } },
                            modifier = Modifier.weight(1f),
                            shapes = shapes,
                        ) {
                            Text(stringResource(iconLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_app_icon_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }

            SettingsSectionCard(title = stringResource(R.string.settings_dark_theme)) {
                val modes = DarkModeOption.entries
                val labelResIds = listOf(R.string.settings_dark_system, R.string.settings_dark_light, R.string.settings_dark_dark)
                ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    modes.forEachIndexed { index, mode ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = settings.darkMode == mode,
                            onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setDarkMode(mode) } },
                            modifier = Modifier.weight(1f),
                            shapes = shapes,
                        ) {
                            Text(stringResource(labelResIds[index]), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_language)) {
                val languages = LanguageOption.entries
                val langLabelResIds = listOf(R.string.settings_language_en, R.string.settings_language_ru)
                ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    languages.forEachIndexed { index, lang ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            languages.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = settings.language == lang,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    haptic()
                                    viewModel.setLanguage(lang)
                                    context.getSharedPreferences("sc_settings", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("language", lang.name).commit()
                                    activity?.recreate()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shapes = shapes,
                        ) {
                            Text(stringResource(langLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_updates)) {
                val channels = UpdateChannel.entries
                val channelLabelResIds = listOf(R.string.settings_update_channel_release, R.string.settings_update_channel_canary)
                ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    channels.forEachIndexed { index, channel ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            channels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = settings.updateChannel == channel,
                            onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setUpdateChannel(channel) } },
                            modifier = Modifier.weight(1f),
                            shapes = shapes,
                        ) {
                            Text(stringResource(channelLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                SettingsDivider()

                SwitchItem(
                    title = stringResource(R.string.settings_auto_check_updates),
                    checked = settings.autoCheckUpdates,
                    onCheckedChange = { viewModel.setAutoCheckUpdates(it) }
                )

                SettingsDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = updateCheckState != UpdateCheckUiState.Checking) { haptic(); viewModel.checkForUpdates() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_check_for_updates), style = MaterialTheme.typography.bodyLarge)
                        if (updateCheckState == UpdateCheckUiState.Checking) {
                            Text(
                                stringResource(R.string.settings_checking_for_updates),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SettingsSectionCard {
                SwitchItem(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.dynamicFromTrack,
                    onCheckedChange = { viewModel.setDynamicFromTrack(it) }
                )
                SettingsDivider()
                SwitchItem(
                    title = stringResource(R.string.settings_autoplay),
                    checked = autoplay,
                    onCheckedChange = { viewModel.setAutoplayNext(it) }
                )
                SettingsDivider()
                SwitchItem(
                    title = stringResource(R.string.settings_haptics),
                    subtitle = stringResource(R.string.settings_haptics_desc),
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { viewModel.setHapticsEnabled(it) }
                )
                if (settings.hapticsEnabled) {
                    Text(
                        stringResource(R.string.settings_haptics_intensity),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    )
                    val intensityOptions = HapticsIntensity.entries
                    val intensityLabelResIds = listOf(
                        R.string.settings_haptics_intensity_low,
                        R.string.settings_haptics_intensity_medium,
                        R.string.settings_haptics_intensity_high,
                    )
                    ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        intensityOptions.forEachIndexed { index, option ->
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                intensityOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            ToggleButton(
                                checked = settings.hapticsIntensity == option,
                                onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setHapticsIntensity(option) } },
                                modifier = Modifier.weight(1f),
                                shapes = shapes,
                            ) {
                                Text(stringResource(intensityLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                SettingsDivider()
                SwitchItem(
                    title = stringResource(R.string.settings_developer_mode),
                    subtitle = stringResource(R.string.settings_developer_mode_desc),
                    checked = settings.developerMode,
                    onCheckedChange = { viewModel.setDeveloperMode(it) }
                )
            }

            if (settings.developerMode && (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "canary")) {
                SettingsSectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { haptic(); onOpenDebugMenu() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_debug_menu), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.settings_debug_menu_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_storage)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_cache), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            cacheSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .clickable {
                                haptic()
                                viewModel.clearCache {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.settings_cache_cleared))
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.settings_clear),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                SettingsDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_offline_tracks), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_offline_tracks_count, offlineCount, offlineSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (offlineCount > 0) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable {
                                    haptic()
                                    viewModel.clearOffline {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.settings_offline_cleared))
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.settings_clear),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            SettingsSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { haptic(); onOpenDislikedArtists() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PersonOff,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.settings_disliked_artists),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isRefreshingClientId) {
                            haptic()
                            viewModel.refreshClientId {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_client_id_refreshed))
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_client_id), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_client_id_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://keepandroidopen.org/")))
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_keep_android_open_title), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_keep_android_open_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(top = 3.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_about)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { haptic(); showAbout = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    if (showAbout) {
        AboutBottomSheet(
            onDismiss = { showAbout = false },
            onOpenUrl = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        )
    }
}

