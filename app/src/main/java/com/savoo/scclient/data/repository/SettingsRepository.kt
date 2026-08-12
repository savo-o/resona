package com.savoo.scclient.data.repository

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.savoo.scclient.data.model.UpdateChannel
import com.savoo.scclient.ui.theme.AppColorTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "sc_settings")

enum class DarkModeOption { SYSTEM, LIGHT, DARK }

enum class AppIconOption { NORMAL, DYNAMIC }

enum class LanguageOption(val locale: Locale?, val displayName: String) {
    ENGLISH(Locale.ENGLISH, "English"),
    RUSSIAN(Locale("ru"), "Русский"),
}

enum class HapticsIntensity { LOW, MEDIUM, HIGH }

data class AppSettings(
    val colorTheme: AppColorTheme = AppColorTheme.DYNAMIC,
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
    val dynamicFromTrack: Boolean = true,
    val developerMode: Boolean = false,
    val language: LanguageOption = LanguageOption.ENGLISH,
    // Manual correction applied on top of the synced lyrics timestamps from the (community-sourced) lyrics
    // provider - positive shifts lines later, negative earlier. Some tracks' data is simply off by a fixed amount.
    val lyricsOffsetMs: Long = 0L,
    val onlineFavoritesEnabled: Boolean = false,
    val updateChannel: UpdateChannel = UpdateChannel.RELEASE,
    val autoCheckUpdates: Boolean = true,
    val appIcon: AppIconOption = AppIconOption.DYNAMIC,
    val hapticsEnabled: Boolean = true,
    val hapticsIntensity: HapticsIntensity = HapticsIntensity.MEDIUM,
    val mixDiscoveryEnabled: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val DYNAMIC_FROM_TRACK = booleanPreferencesKey("dynamic_from_track")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val LYRICS_OFFSET_MS = longPreferencesKey("lyrics_offset_ms")
        val ONLINE_FAVORITES_ENABLED = booleanPreferencesKey("online_favorites_enabled")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val APP_ICON = stringPreferencesKey("app_icon")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HAPTICS_INTENSITY = stringPreferencesKey("haptics_intensity")
        val MIX_DISCOVERY_ENABLED = booleanPreferencesKey("mix_discovery_enabled")
    }

    val settings = context.dataStore.data.map { prefs ->
        AppSettings(
            colorTheme = prefs[Keys.COLOR_THEME]?.let {
                runCatching { AppColorTheme.valueOf(it) }.getOrNull()
            } ?: AppColorTheme.DYNAMIC,
            darkMode = prefs[Keys.DARK_MODE]?.let {
                runCatching { DarkModeOption.valueOf(it) }.getOrNull()
            } ?: DarkModeOption.SYSTEM,
            dynamicFromTrack = prefs[Keys.DYNAMIC_FROM_TRACK] ?: true,
            developerMode = prefs[Keys.DEVELOPER_MODE] ?: false,
            language = prefs[Keys.LANGUAGE]?.let {
                runCatching { LanguageOption.valueOf(it) }.getOrNull()
            } ?: LanguageOption.ENGLISH,
            lyricsOffsetMs = prefs[Keys.LYRICS_OFFSET_MS] ?: 0L,
            onlineFavoritesEnabled = prefs[Keys.ONLINE_FAVORITES_ENABLED] ?: false,
            updateChannel = prefs[Keys.UPDATE_CHANNEL]?.let {
                runCatching { UpdateChannel.valueOf(it) }.getOrNull()
            } ?: UpdateChannel.RELEASE,
            autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
            appIcon = prefs[Keys.APP_ICON]?.let {
                runCatching { AppIconOption.valueOf(it) }.getOrNull()
            } ?: AppIconOption.DYNAMIC,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            hapticsIntensity = prefs[Keys.HAPTICS_INTENSITY]?.let {
                runCatching { HapticsIntensity.valueOf(it) }.getOrNull()
            } ?: HapticsIntensity.MEDIUM,
            mixDiscoveryEnabled = prefs[Keys.MIX_DISCOVERY_ENABLED] ?: true,
        )
    }

    val autoplayNext = context.dataStore.data.map { it[Keys.AUTOPLAY_NEXT] ?: true }

    suspend fun setColorTheme(theme: AppColorTheme) {
        context.dataStore.edit { it[Keys.COLOR_THEME] = theme.name }
    }

    suspend fun setDarkMode(mode: DarkModeOption) {
        context.dataStore.edit { it[Keys.DARK_MODE] = mode.name }
    }

    suspend fun setAutoplayNext(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTOPLAY_NEXT] = value }
    }

    suspend fun setDynamicFromTrack(value: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_FROM_TRACK] = value }
    }

    suspend fun setDeveloperMode(value: Boolean) {
        context.dataStore.edit { it[Keys.DEVELOPER_MODE] = value }
    }

    suspend fun setLanguage(language: LanguageOption) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setLyricsOffsetMs(offsetMs: Long) {
        context.dataStore.edit { it[Keys.LYRICS_OFFSET_MS] = offsetMs }
    }

    suspend fun setOnlineFavoritesEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.ONLINE_FAVORITES_ENABLED] = value }
    }

    suspend fun setUpdateChannel(channel: UpdateChannel) {
        context.dataStore.edit { it[Keys.UPDATE_CHANNEL] = channel.name }
    }

    suspend fun setAutoCheckUpdates(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = value }
    }

    suspend fun setAppIcon(option: AppIconOption) {
        context.dataStore.edit { it[Keys.APP_ICON] = option.name }
    }

    suspend fun setHapticsEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = value }
    }

    suspend fun setHapticsIntensity(value: HapticsIntensity) {
        context.dataStore.edit { it[Keys.HAPTICS_INTENSITY] = value.name }
    }

    suspend fun setMixDiscoveryEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.MIX_DISCOVERY_ENABLED] = value }
    }
}
