package com.savoo.scclient.ui.screens.settings

import com.savoo.scclient.ui.components.ChipLabel
import com.savoo.scclient.ui.navigation.aboveDock
import com.savoo.scclient.ui.navigation.dockInset
import com.savoo.scclient.compat.*
import kotlinx.coroutines.IO
import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.Uri
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import com.savoo.scclient.i18n.CustomStrings
import com.savoo.scclient.i18n.CustomStringsCheck
import com.savoo.scclient.i18n.CustomStringsStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.savoo.scclient.ui.navigation.SoundCloudLinks
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.savoo.scclient.resources.*
import com.savoo.scclient.data.model.UpdateChannel
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.data.repository.AppIconManager
import com.savoo.scclient.data.repository.AppBackgroundMode
import com.savoo.scclient.data.repository.AppIconOption
import com.savoo.scclient.data.repository.AppSettings
import com.savoo.scclient.data.repository.CacheLimitOptionsMb
import com.savoo.scclient.data.repository.MaxCrossfadeSeconds
import com.savoo.scclient.data.repository.MinCrossfadeSeconds
import com.savoo.scclient.data.repository.DarkModeOption
import com.savoo.scclient.data.repository.HapticsIntensity
import com.savoo.scclient.data.repository.HomeSectionConfig
import com.savoo.scclient.data.repository.LanguageOption
import com.savoo.scclient.data.repository.ArtworkGlowSource
import com.savoo.scclient.data.repository.TimedCommentsRate
import com.savoo.scclient.data.repository.DividerStyle
import com.savoo.scclient.data.repository.DrmTrackHiding
import com.savoo.scclient.data.repository.PlayerBackgroundStyle
import com.savoo.scclient.ui.components.AppDivider
import com.savoo.scclient.data.repository.PlayerStyle
import com.savoo.scclient.data.repository.SeekBarStyle
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.UpdateCheckResult
import com.savoo.scclient.data.repository.UpdateRepository
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.TrackCache
import com.savoo.scclient.ui.components.SwitchItem
import com.savoo.scclient.ui.haptics.rememberHapticTick
import com.savoo.scclient.ui.haptics.rememberHaptics
import com.savoo.scclient.ui.theme.AppColorTheme
import com.savoo.scclient.compat.BuildConfig
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.SystemUpdate
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.savoo.scclient.compat.File
import androidx.compose.ui.text.style.TextOverflow

sealed interface UpdateCheckUiState {
    data object Idle : UpdateCheckUiState
    data object Checking : UpdateCheckUiState
    data class Available(val result: UpdateCheckResult.Available) : UpdateCheckUiState
    data object UpToDate : UpdateCheckUiState
    data object Error : UpdateCheckUiState
}
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val offlineTrackManager: OfflineTrackManager,
    private val clientIdProvider: ClientIdProvider,
    private val trackCache: TrackCache,
    private val context: Context,
) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val autoplayNext = repository.autoplayNext.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _updateCheckState = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val updateCheckState = _updateCheckState.asStateFlow()

    private val _cacheSize = MutableStateFlow(context.getString(Res.string.calculating))
    val cacheSize = _cacheSize.asStateFlow()

    private val _offlineSize = MutableStateFlow(context.getString(Res.string.calculating))
    val offlineSize = _offlineSize.asStateFlow()

    private val _offlineCount = MutableStateFlow(0)
    val offlineCount = _offlineCount.asStateFlow()

    private val _isRefreshingClientId = MutableStateFlow(false)
    val isRefreshingClientId = _isRefreshingClientId.asStateFlow()
    private val _clientId = MutableStateFlow(clientIdProvider.cachedOrFallback())
    val clientId = _clientId.asStateFlow()

    init {
        calculateCacheSize()
        calculateOfflineSize()
    }

    fun setColorTheme(theme: AppColorTheme) = viewModelScope.launch { repository.setColorTheme(theme) }
    fun setDarkMode(mode: DarkModeOption) = viewModelScope.launch { repository.setDarkMode(mode) }
    fun setAutoplayNext(value: Boolean) = viewModelScope.launch { repository.setAutoplayNext(value) }
    fun setDynamicFromTrack(value: Boolean) = viewModelScope.launch { repository.setDynamicFromTrack(value) }
    fun setLiquidGlass(value: Boolean) = viewModelScope.launch { repository.setLiquidGlass(value) }
    fun setDockBackground(value: Boolean) = viewModelScope.launch { repository.setDockBackground(value) }
    fun setDeveloperMode(value: Boolean) = viewModelScope.launch { repository.setDeveloperMode(value) }
    fun setHapticsEnabled(value: Boolean) = viewModelScope.launch { repository.setHapticsEnabled(value) }
    fun setHapticsIntensity(value: HapticsIntensity) = viewModelScope.launch { repository.setHapticsIntensity(value) }
    fun setUpdateChannel(channel: UpdateChannel) = viewModelScope.launch { repository.setUpdateChannel(channel) }
    fun setAutoCheckUpdates(value: Boolean) = viewModelScope.launch { repository.setAutoCheckUpdates(value) }
    fun setAppIcon(option: AppIconOption) = viewModelScope.launch {
        repository.setAppIcon(option)
        AppIconManager.apply(context, option)
    }
    fun setCrossfadeEnabled(value: Boolean) = viewModelScope.launch { repository.setCrossfadeEnabled(value) }
    fun setPauseForOtherApps(value: Boolean) = viewModelScope.launch { repository.setPauseForOtherApps(value) }
    fun setSeekBarStyle(style: SeekBarStyle) = viewModelScope.launch { repository.setSeekBarStyle(style) }
    fun setGeniusFallbackEnabled(value: Boolean) = viewModelScope.launch { repository.setGeniusFallbackEnabled(value) }
    fun setGeniusAnnotationsEnabled(value: Boolean) = viewModelScope.launch { repository.setGeniusAnnotationsEnabled(value) }
    fun setCustomSeedColor(color: Color) = viewModelScope.launch { repository.setCustomSeedColor(color) }
    fun setHomeSections(sections: List<HomeSectionConfig>) = viewModelScope.launch { repository.setHomeSections(sections) }
    fun setPlayerBackgroundStyle(style: PlayerBackgroundStyle) = viewModelScope.launch { repository.setPlayerBackgroundStyle(style) }
    fun setArtworkScalePercent(value: Int) = viewModelScope.launch { repository.setArtworkScalePercent(value) }
    fun setFontRoundnessEnabled(value: Boolean) = viewModelScope.launch { repository.setFontRoundnessEnabled(value) }
    fun setFontRoundness(value: Int) = viewModelScope.launch { repository.setFontRoundness(value) }
    fun setArtworkRingEnabled(value: Boolean) = viewModelScope.launch { repository.setArtworkRingEnabled(value) }
    fun setArtworkShape(shape: com.savoo.scclient.data.repository.ArtworkShape) = viewModelScope.launch { repository.setArtworkShape(shape) }
    fun setDividerStyle(style: DividerStyle) = viewModelScope.launch { repository.setDividerStyle(style) }
    fun setArtworkGlowSource(source: ArtworkGlowSource) = viewModelScope.launch { repository.setArtworkGlowSource(source) }
    fun setMarqueeTitles(value: Boolean) = viewModelScope.launch { repository.setMarqueeTitles(value) }
    fun setTimedCommentsEnabled(value: Boolean) = viewModelScope.launch { repository.setTimedCommentsEnabled(value) }
    fun setTimedCommentsRate(rate: TimedCommentsRate) = viewModelScope.launch { repository.setTimedCommentsRate(rate) }
    fun setDrmTrackHiding(mode: DrmTrackHiding) = viewModelScope.launch { repository.setDrmTrackHiding(mode) }

    data class CustomTranslationState(
        val present: Boolean = false,
        val stats: CustomStringsStats? = null,
        val failed: Boolean = false,
        val tooLarge: Boolean = false,
        val checking: Boolean = false,
        val templateSaved: Boolean = false,
        val pendingRestart: Boolean = false,
        val pendingCheck: CustomStringsCheck? = null,
    )

    private val _customTranslation = MutableStateFlow(
        CustomTranslationState(present = CustomStrings.hasFile(context), stats = CustomStrings.stats)
    )
    val customTranslation = _customTranslation.asStateFlow()

    fun installCustomTranslation(uri: Uri) {
        viewModelScope.launch {
            val current = _customTranslation.value
            _customTranslation.value = current.copy(checking = true, failed = false, tooLarge = false)
            val check = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                    ?.let { CustomStrings.inspect(context, it) }
            }
            val error = check?.exceptionOrNull()
            when {
                check == null || error is OutOfMemoryError -> _customTranslation.value =
                    current.copy(failed = true)
                error != null -> _customTranslation.value = current.copy(failed = true)
                check.getOrThrow().hasWarnings -> _customTranslation.value =
                    current.copy(pendingCheck = check.getOrThrow())
                else -> applyCustomTranslation(check.getOrThrow(), skipProblems = false)
            }
        }
    }

    fun confirmCustomTranslation(skipProblems: Boolean) {
        val check = _customTranslation.value.pendingCheck ?: return
        _customTranslation.value = _customTranslation.value.copy(pendingCheck = null, checking = true)
        viewModelScope.launch { applyCustomTranslation(check, skipProblems) }
    }

    fun cancelCustomTranslation() {
        _customTranslation.value = _customTranslation.value.copy(pendingCheck = null, checking = false)
    }

    private suspend fun applyCustomTranslation(check: CustomStringsCheck, skipProblems: Boolean) {
        withContext(Dispatchers.IO) { CustomStrings.install(context, check, skipProblems) }
            .onSuccess { stats ->
                _customTranslation.value =
                    CustomTranslationState(present = true, stats = stats, pendingRestart = true)
            }
            .onFailure {
                _customTranslation.value = _customTranslation.value.copy(failed = true, checking = false)
            }
    }

    fun clearCustomTranslation() {
        CustomStrings.clear(context)
        _customTranslation.value = CustomTranslationState()
    }

    fun consumeCustomTranslationRestart() {
        _customTranslation.value = _customTranslation.value.copy(pendingRestart = false)
    }

    fun exportTranslationTemplate(uri: Uri) {
        viewModelScope.launch {
            val saved = false
            _customTranslation.value = _customTranslation.value.copy(
                templateSaved = saved,
                failed = !saved,
            )
        }
    }
    fun setPlayerStyle(style: PlayerStyle) = viewModelScope.launch { repository.setPlayerStyle(style) }
    fun setBackgroundMode(mode: AppBackgroundMode) = viewModelScope.launch { repository.setBackgroundMode(mode) }
    fun setPixelGlowEnabled(value: Boolean) = viewModelScope.launch { repository.setPixelGlowEnabled(value) }
    fun setBeatPulseEnabled(value: Boolean) = viewModelScope.launch { repository.setBeatPulseEnabled(value) }
    fun setAnimatedArtwork(value: Boolean) = viewModelScope.launch { repository.setAnimatedArtwork(value) }
    fun setRecapEnabled(value: Boolean) = viewModelScope.launch { repository.setRecapEnabled(value) }
    fun setCacheLimitMb(limitMb: Int) = viewModelScope.launch { repository.setCacheLimitMb(limitMb) }
    fun setCrossfadeSeconds(seconds: Int) = viewModelScope.launch { repository.setCrossfadeSeconds(seconds) }
    fun setBackgroundCustomColor(color: Color) = viewModelScope.launch { repository.setBackgroundCustomColor(color) }

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
        context.getSharedPreferences("sc_settings", Context.MODE_PRIVATE)
            .edit().putString("language", language.name).commit()
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                var total = 0L
                total += getDirSize(context.cacheDir)
                formatSize(total)
            }
            _cacheSize.value = size
        }
    }

    fun clearCache(onDone: () -> Unit) {
        viewModelScope.launch {
            trackCache.clearCache()
            withContext(Dispatchers.IO) {
                deleteDir(context.cacheDir)
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

    fun loadClientId() {
        _clientId.value = clientIdProvider.cachedOrFallback()
    }

    fun refreshClientId(onDone: (StringResource) -> Unit) = performClientIdAction(onDone) {
        clientIdProvider.refresh(force = true, failOnError = true)
        Res.string.settings_client_id_refreshed
    }

    fun clearClientId(onDone: (StringResource) -> Unit) = performClientIdAction(onDone) {
        clientIdProvider.clear()
        Res.string.settings_client_id_cleared
    }

    fun saveClientId(value: String, onDone: (StringResource) -> Unit) = performClientIdAction(onDone) {
        require(value.matches(Regex("[a-zA-Z0-9]{32}")))
        clientIdProvider.setManualOverride(value)
        Res.string.settings_client_id_saved
    }

    private fun performClientIdAction(onDone: (StringResource) -> Unit, action: suspend () -> StringResource) {
        if (_isRefreshingClientId.value) return
        _isRefreshingClientId.value = true
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) { action() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Res.string.settings_client_id_failed
            } finally {
                _clientId.value = clientIdProvider.cachedOrFallback()
                _isRefreshingClientId.value = false
            }
            onDone(message)
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

private const val TRANSLATION_CATALOG_URL = "https://savo-o.github.io/resona-status/translations/"

@Composable
internal fun SettingsDivider() {
    AppDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun cacheLimitLabel(limitMb: Int): String =
    if (limitMb >= 1024) stringResource(Res.string.settings_cache_limit_gb, limitMb / 1024)
    else stringResource(Res.string.settings_cache_limit_mb, limitMb)

@Composable
internal fun SettingsSectionCard(
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onBack: () -> Unit = {},
    onOpenDebugMenu: () -> Unit = {},
    onOpenDislikedArtists: () -> Unit = {},
    onOpenCustomization: () -> Unit = {},
    onOpenAppLock: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val autoplay by viewModel.autoplayNext.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val offlineCount by viewModel.offlineCount.collectAsState()
    val offlineSize by viewModel.offlineSize.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val isRefreshingClientId by viewModel.isRefreshingClientId.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    var showClientIdActions by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showKeepAndroidOpen by remember { mutableStateOf(false) }
    var showEula by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var linksEnabled by remember { mutableStateOf(SoundCloudLinks.isHandlingEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        linksEnabled = SoundCloudLinks.isHandlingEnabled(context)
        onPauseOrDispose { }
    }
    val activity = AppRestart
    val haptic = rememberHapticTick()
    val haptics = rememberHaptics()

    val translationTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { uri -> uri?.let { viewModel.exportTranslationTemplate(it) } }

    val translationFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.installCustomTranslation(it) } }

    LaunchedEffect(updateCheckState) {
        when (val state = updateCheckState) {
            UpdateCheckUiState.UpToDate -> {
                snackbarHostState.showSnackbar(context.getString(Res.string.settings_update_up_to_date))
                viewModel.consumeUpdateCheckState()
            }
            UpdateCheckUiState.Error -> {
                haptics.error()
                snackbarHostState.showSnackbar(context.getString(Res.string.settings_update_check_failed))
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
                title = { Text(stringResource(Res.string.settings_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.aboveDock()) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = dockInset()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            SettingsSectionCard(title = stringResource(Res.string.settings_language)) {
                val languages = LanguageOption.entries.filter { it != LanguageOption.CUSTOM }
                val langLabelResIds = listOf(
                    Res.string.settings_language_en,
                    Res.string.settings_language_ru,
                    Res.string.settings_language_custom,
                )
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
                                    context.getSharedPreferences("sc_settings", Context.MODE_PRIVATE)
                                        .edit().putString("language", lang.name).commit()
                                    activity.recreate()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shapes = shapes,
                        ) {
                            ChipLabel(stringResource(langLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                if (settings.language == LanguageOption.CUSTOM) {
                    val custom by viewModel.customTranslation.collectAsState()

                    LaunchedEffect(custom.pendingRestart) {
                        if (custom.pendingRestart) {
                            viewModel.consumeCustomTranslationRestart()
                            activity.recreate()
                        }
                    }

                    custom.pendingCheck?.let { check ->
                        CustomTranslationWarningDialog(
                            check = check,
                            onSkipProblems = { viewModel.confirmCustomTranslation(skipProblems = true) },
                            onLoadAnyway = { viewModel.confirmCustomTranslation(skipProblems = false) },
                            onCancel = { viewModel.cancelCustomTranslation() },
                        )
                    }

                    SettingsDivider()

                    Text(
                        stringResource(Res.string.custom_language_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(Res.string.custom_language_official_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(TRANSLATION_CATALOG_URL))
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(Res.string.custom_language_catalog),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(Res.string.custom_language_catalog_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                translationTemplateLauncher.launch("strings_template.xml")
                            }
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
                        Text(
                            stringResource(Res.string.custom_language_template),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                translationFileLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    if (custom.present) Res.string.custom_language_replace
                                    else Res.string.custom_language_pick
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val status = when {
                                custom.checking -> stringResource(Res.string.custom_language_checking)
                                custom.tooLarge -> stringResource(Res.string.custom_language_too_large)
                                custom.failed -> stringResource(Res.string.custom_language_failed)
                                custom.templateSaved && !custom.present ->
                                    stringResource(Res.string.custom_language_template_saved)
                                custom.stats != null -> stringResource(
                                    Res.string.custom_language_stats,
                                    custom.stats!!.applied,
                                    custom.stats!!.total,
                                )
                                custom.present -> stringResource(Res.string.custom_language_loaded)
                                else -> stringResource(Res.string.custom_language_none)
                            }
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            custom.stats?.takeIf { it.unknown > 0 }?.let { stats ->
                                Text(
                                    stringResource(Res.string.custom_language_unknown, stats.unknown),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(Res.string.custom_language_missing_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    if (custom.present) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.clearCustomTranslation()
                                    activity.recreate()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                stringResource(Res.string.custom_language_clear),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            SettingsSectionCard(title = stringResource(Res.string.settings_updates)) {
                val channels = UpdateChannel.entries
                val channelLabelResIds = listOf(Res.string.settings_update_channel_release, Res.string.settings_update_channel_canary)
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
                            ChipLabel(stringResource(channelLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                SettingsDivider()

                SwitchItem(
                    title = stringResource(Res.string.settings_auto_check_updates),
                    checked = settings.autoCheckUpdates,
                    onCheckedChange = { viewModel.setAutoCheckUpdates(it) }
                )

                SettingsDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = updateCheckState != UpdateCheckUiState.Checking) { viewModel.checkForUpdates() }
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
                        Text(stringResource(Res.string.settings_check_for_updates), style = MaterialTheme.typography.bodyLarge)
                        if (updateCheckState == UpdateCheckUiState.Checking) {
                            Text(
                                stringResource(Res.string.settings_checking_for_updates),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SettingsSectionCard {
                SwitchItem(
                    title = stringResource(Res.string.settings_autoplay),
                    checked = autoplay,
                    onCheckedChange = { viewModel.setAutoplayNext(it) }
                )
                SettingsDivider()
                SwitchItem(
                    title = stringResource(Res.string.settings_pause_for_other_apps),
                    subtitle = stringResource(Res.string.settings_pause_for_other_apps_desc),
                    checked = settings.pauseForOtherApps,
                    onCheckedChange = { viewModel.setPauseForOtherApps(it) }
                )
                SettingsDivider()
                SwitchItem(
                    title = stringResource(Res.string.settings_crossfade),
                    subtitle = stringResource(Res.string.settings_crossfade_desc),
                    checked = settings.crossfadeEnabled,
                    onCheckedChange = { viewModel.setCrossfadeEnabled(it) }
                )
                if (settings.crossfadeEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.settings_crossfade_duration),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(Res.string.settings_crossfade_duration_format, settings.crossfadeSeconds),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = settings.crossfadeSeconds.toFloat(),
                        onValueChange = { viewModel.setCrossfadeSeconds(it.roundToInt()) },
                        onValueChangeFinished = { haptic() },
                        valueRange = MinCrossfadeSeconds.toFloat()..MaxCrossfadeSeconds.toFloat(),
                        steps = MaxCrossfadeSeconds - MinCrossfadeSeconds - 1,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                SettingsDivider()
                SwitchItem(
                    title = stringResource(Res.string.settings_timed_comments),
                    subtitle = stringResource(Res.string.settings_timed_comments_desc),
                    checked = settings.timedCommentsEnabled,
                    onCheckedChange = { viewModel.setTimedCommentsEnabled(it) }
                )
                if (settings.timedCommentsEnabled) {
                    val rates = listOf(
                        TimedCommentsRate.OFTEN,
                        TimedCommentsRate.NORMAL,
                        TimedCommentsRate.RARE,
                        TimedCommentsRate.EXACT,
                    )
                    val rateLabels = listOf(
                        Res.string.settings_timed_comments_often,
                        Res.string.settings_timed_comments_normal,
                        Res.string.settings_timed_comments_rare,
                        Res.string.settings_timed_comments_exact,
                    )
                    ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        rates.forEachIndexed { index, rate ->
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                rates.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            ToggleButton(
                                checked = settings.timedCommentsRate == rate,
                                onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setTimedCommentsRate(rate) } },
                                modifier = Modifier.weight(1f),
                                shapes = shapes,
                            ) {
                                ChipLabel(stringResource(rateLabels[index]), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                SettingsDivider()
                SwitchItem(
                    title = stringResource(Res.string.settings_lyrics_genius_fallback),
                    subtitle = stringResource(Res.string.settings_lyrics_genius_fallback_desc),
                    checked = settings.geniusFallbackEnabled,
                    onCheckedChange = { viewModel.setGeniusFallbackEnabled(it) }
                )
                SettingsDivider()
                SwitchItem(
                    title = stringResource(Res.string.settings_lyrics_genius_annotations),
                    subtitle = stringResource(Res.string.settings_lyrics_genius_annotations_desc),
                    checked = settings.geniusAnnotationsEnabled,
                    onCheckedChange = { viewModel.setGeniusAnnotationsEnabled(it) }
                )
                SettingsDivider()
                SwitchItem(
                    title = stringResource(Res.string.settings_recap),
                    subtitle = stringResource(Res.string.settings_recap_desc),
                    checked = settings.recapEnabled,
                    onCheckedChange = { viewModel.setRecapEnabled(it) }
                )
                SettingsDivider()
                SwitchItem(
                    title = stringResource(Res.string.settings_haptics),
                    subtitle = stringResource(Res.string.settings_haptics_desc),
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { viewModel.setHapticsEnabled(it) }
                )
                if (settings.hapticsEnabled) {
                    Text(
                        stringResource(Res.string.settings_haptics_intensity),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    )
                    val intensityOptions = HapticsIntensity.entries
                    val intensityLabelResIds = listOf(
                        Res.string.settings_haptics_intensity_low,
                        Res.string.settings_haptics_intensity_medium,
                        Res.string.settings_haptics_intensity_high,
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
                                ChipLabel(stringResource(intensityLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                SettingsDivider()
                SwitchItem(
                    title = stringResource(Res.string.settings_developer_mode),
                    subtitle = stringResource(Res.string.settings_developer_mode_desc),
                    checked = settings.developerMode,
                    onCheckedChange = { viewModel.setDeveloperMode(it) }
                )
            }

            if (settings.developerMode) {
                SettingsSectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDebugMenu() }
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
                            Text(stringResource(Res.string.settings_debug_menu), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(Res.string.settings_debug_menu_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SettingsSectionCard(title = stringResource(Res.string.settings_storage)) {
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
                        Text(stringResource(Res.string.settings_cache), style = MaterialTheme.typography.bodyLarge)
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
                                viewModel.clearCache {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(Res.string.settings_cache_cleared))
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
                            stringResource(Res.string.settings_clear),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Text(
                    stringResource(Res.string.settings_cache_limit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                )
                Text(
                    stringResource(Res.string.settings_cache_limit_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
                )
                ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    CacheLimitOptionsMb.forEachIndexed { index, limitMb ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            CacheLimitOptionsMb.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = settings.cacheLimitMb == limitMb,
                            onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setCacheLimitMb(limitMb) } },
                            modifier = Modifier.weight(1f),
                            shapes = shapes,
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            ChipLabel(
                                cacheLimitLabel(limitMb),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
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
                        Text(stringResource(Res.string.settings_offline_tracks), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.settings_offline_tracks_count, offlineCount, offlineSize),
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
                                    viewModel.clearOffline {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(Res.string.settings_offline_cleared))
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
                                stringResource(Res.string.settings_clear),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            SettingsSectionCard(title = stringResource(Res.string.settings_drm_hiding)) {
                Text(
                    stringResource(Res.string.settings_drm_hiding_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                val drmModes = DrmTrackHiding.entries
                val drmLabelResIds = listOf(
                    Res.string.settings_drm_hiding_full,
                    Res.string.settings_drm_hiding_partial,
                    Res.string.settings_drm_hiding_off,
                )
                val drmDescResIds = listOf(
                    Res.string.settings_drm_hiding_full_desc,
                    Res.string.settings_drm_hiding_partial_desc,
                    Res.string.settings_drm_hiding_off_desc,
                )
                ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    drmModes.forEachIndexed { index, mode ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            drmModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = settings.drmTrackHiding == mode,
                            onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setDrmTrackHiding(mode) } },
                            modifier = Modifier.weight(1f),
                            shapes = shapes,
                        ) {
                            ChipLabel(
                                stringResource(drmLabelResIds[index]),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                Text(
                    stringResource(drmDescResIds[drmModes.indexOf(settings.drmTrackHiding).coerceAtLeast(0)]),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                )
            }

            SettingsSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCustomization() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(Res.string.settings_customization),
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
                        .clickable { onOpenDislikedArtists() }
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
                        stringResource(Res.string.settings_disliked_artists),
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
                        .clickable { viewModel.loadClientId(); showClientIdActions = true }
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
                        Text(stringResource(Res.string.settings_client_id_advanced), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.settings_client_id_advanced_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        .clickable { showKeepAndroidOpen = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.settings_keep_android_open_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.settings_keep_android_open_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSectionCard(title = stringResource(Res.string.settings_about)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAbout = true }
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
                        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEula = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Gavel,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.settings_eula), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.settings_eula_desc),
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

    if (showEula) {
        EulaBottomSheet(onDismiss = { showEula = false })
    }

    if (showClientIdActions) {
        val onClientIdDone: (StringResource) -> Unit = { message ->
            scope.launch { snackbarHostState.showSnackbar(context.getString(message)) }
        }
        ClientIdActionsDialog(
            clientId = clientId,
            busy = isRefreshingClientId,
            onRefresh = { viewModel.refreshClientId(onClientIdDone) },
            onClear = { viewModel.clearClientId(onClientIdDone) },
            onSave = { viewModel.saveClientId(it, onClientIdDone) },
            onDismiss = { showClientIdActions = false },
        )
    }

    if (showKeepAndroidOpen) {
        AlertDialog(
            onDismissRequest = { showKeepAndroidOpen = false },
            title = { Text(stringResource(Res.string.settings_keep_android_open_title)) },
            text = { Text(stringResource(Res.string.settings_keep_android_open_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showKeepAndroidOpen = false
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://keepandroidopen.org/")))
                }) {
                    Text(stringResource(Res.string.settings_keep_android_open_learn_more))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeepAndroidOpen = false }) {
                    Text(stringResource(Res.string.close))
                }
            },
        )
    }
}

