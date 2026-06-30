package com.savoo.scclient.data.repository

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.savoo.scclient.ui.theme.AppColorTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "sc_settings")

enum class DarkModeOption { SYSTEM, LIGHT, DARK }

enum class LanguageOption(val locale: Locale?, val displayName: String) {
    ENGLISH(Locale.ENGLISH, "English"),
    RUSSIAN(Locale("ru"), "Русский"),
}

data class AppSettings(
    val colorTheme: AppColorTheme = AppColorTheme.DYNAMIC,
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
    val dynamicFromTrack: Boolean = true,
    val developerMode: Boolean = false,
    val language: LanguageOption = LanguageOption.ENGLISH,
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
}
