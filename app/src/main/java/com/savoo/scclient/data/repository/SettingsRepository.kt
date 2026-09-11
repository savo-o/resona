package com.savoo.scclient.data.repository

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.savoo.scclient.data.model.UpdateChannel
import com.savoo.scclient.ui.theme.AppColorTheme
import com.savoo.scclient.ui.theme.OrangeSeed
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

// Only used when the user has never explicitly chosen a language: Russian devices default to Russian,
// everything else (including English) defaults to English.
fun systemDefaultLanguage(context: Context): LanguageOption =
    if (context.resources.configuration.locales[0].language == "ru") LanguageOption.RUSSIAN else LanguageOption.ENGLISH

enum class HapticsIntensity { LOW, MEDIUM, HIGH }

enum class LyricsProvider { LRCLIB, KUGOU }

enum class SeekBarStyle { CLASSIC, WAVY }

enum class PlayerBackgroundStyle { ORB, BLURRED_ARTWORK, MINIMAL }

enum class PlayerStyle { CLASSIC, PIXEL }

enum class AppBackgroundMode { DYNAMIC, DEFAULT, CUSTOM, PLAYER_ONLY }

enum class DividerStyle { HIDDEN, SUBTLE, BRIGHT }

enum class HomeSection { JUMP_BACK_IN, FAVORITES, OFFLINE, ARTISTS, PLAYLISTS }

val CacheLimitOptionsMb = listOf(256, 512, 1024, 2048)
const val DefaultCacheLimitMb = 512

const val DefaultCrossfadeSeconds = 2
const val MinCrossfadeSeconds = 1
const val MaxCrossfadeSeconds = 12

data class HomeSectionConfig(val section: HomeSection, val visible: Boolean = true)

val DefaultHomeSections = HomeSection.entries.map { HomeSectionConfig(it) }

private fun serializeHomeSections(sections: List<HomeSectionConfig>): String =
    sections.joinToString(",") { (if (!it.visible) "-" else "") + it.section.name }

private fun parseHomeSections(raw: String?): List<HomeSectionConfig> {
    if (raw.isNullOrBlank()) return DefaultHomeSections
    val parsed = raw.split(",").mapNotNull { token ->
        val visible = !token.startsWith("-")
        val name = token.removePrefix("-")
        runCatching { HomeSection.valueOf(name) }.getOrNull()?.let { HomeSectionConfig(it, visible) }
    }
    // Cover app updates that add a new section after this list was already saved on disk.
    val missing = HomeSection.entries.filter { section -> parsed.none { it.section == section } }
        .map { HomeSectionConfig(it) }
    return parsed + missing
}

data class AppSettings(
    val colorTheme: AppColorTheme = AppColorTheme.DYNAMIC,
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
    val dynamicFromTrack: Boolean = true,
    val developerMode: Boolean = false,
    val language: LanguageOption = LanguageOption.ENGLISH,
    // Manual correction applied on top of the synced lyrics timestamps from the (community-sourced) lyrics
    // provider - positive shifts lines later, negative earlier. Some tracks' data is simply off by a fixed amount.
    val lyricsOffsetMs: Long = 0L,
    val lyricsProvider: LyricsProvider = LyricsProvider.LRCLIB,
    val geniusFallbackEnabled: Boolean = true,
    val onlineFavoritesEnabled: Boolean = false,
    val updateChannel: UpdateChannel = UpdateChannel.RELEASE,
    val autoCheckUpdates: Boolean = true,
    val appIcon: AppIconOption = AppIconOption.DYNAMIC,
    val hapticsEnabled: Boolean = true,
    val hapticsIntensity: HapticsIntensity = HapticsIntensity.MEDIUM,
    val mixDiscoveryEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val eulaAccepted: Boolean = false,
    val crossfadeEnabled: Boolean = false,
    val seekBarStyle: SeekBarStyle = SeekBarStyle.WAVY,
    val customSeedColor: Color = OrangeSeed.Primary,
    val homeSections: List<HomeSectionConfig> = DefaultHomeSections,
    val playerBackgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.ORB,
    val playerStyle: PlayerStyle = PlayerStyle.PIXEL,
    val backgroundMode: AppBackgroundMode = AppBackgroundMode.DYNAMIC,
    val backgroundCustomColor: Color = Color(0xFF1B1B1F),
    val playerHintShown: Boolean = false,
    val pixelGlowEnabled: Boolean = true,
    val cacheLimitMb: Int = DefaultCacheLimitMb,
    val crossfadeSeconds: Int = DefaultCrossfadeSeconds,
    val dividerStyle: DividerStyle = DividerStyle.SUBTLE,
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
        val LYRICS_PROVIDER = stringPreferencesKey("lyrics_provider")
        val GENIUS_FALLBACK_ENABLED = booleanPreferencesKey("genius_fallback_enabled")
        val ONLINE_FAVORITES_ENABLED = booleanPreferencesKey("online_favorites_enabled")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val APP_ICON = stringPreferencesKey("app_icon")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HAPTICS_INTENSITY = stringPreferencesKey("haptics_intensity")
        val MIX_DISCOVERY_ENABLED = booleanPreferencesKey("mix_discovery_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val EULA_ACCEPTED = booleanPreferencesKey("eula_accepted")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val SEEK_BAR_STYLE = stringPreferencesKey("seek_bar_style")
        val CUSTOM_SEED_COLOR = intPreferencesKey("custom_seed_color")
        val HOME_SECTIONS = stringPreferencesKey("home_sections")
        val PLAYER_BACKGROUND_STYLE = stringPreferencesKey("player_background_style")
        val PLAYER_STYLE = stringPreferencesKey("player_style")
        val BACKGROUND_MODE = stringPreferencesKey("background_mode")
        val BACKGROUND_CUSTOM_COLOR = intPreferencesKey("background_custom_color")
        val PLAYER_HINT_SHOWN = booleanPreferencesKey("player_hint_shown")
        val PIXEL_GLOW_ENABLED = booleanPreferencesKey("pixel_glow_enabled")
        val CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val DIVIDER_STYLE = stringPreferencesKey("divider_style")
    }

    private fun hasPreExistingSettings(prefs: androidx.datastore.preferences.core.Preferences): Boolean =
        prefs.asMap().keys.any { it.name != Keys.ONBOARDING_COMPLETED.name && it.name != Keys.EULA_ACCEPTED.name }

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
            } ?: systemDefaultLanguage(context),
            lyricsOffsetMs = prefs[Keys.LYRICS_OFFSET_MS] ?: 0L,
            lyricsProvider = prefs[Keys.LYRICS_PROVIDER]?.let {
                runCatching { LyricsProvider.valueOf(it) }.getOrNull()
            } ?: LyricsProvider.LRCLIB,
            geniusFallbackEnabled = prefs[Keys.GENIUS_FALLBACK_ENABLED] ?: true,
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
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: hasPreExistingSettings(prefs),
            eulaAccepted = prefs[Keys.EULA_ACCEPTED] ?: hasPreExistingSettings(prefs),
            crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
            seekBarStyle = prefs[Keys.SEEK_BAR_STYLE]?.let {
                runCatching { SeekBarStyle.valueOf(it) }.getOrNull()
            } ?: SeekBarStyle.WAVY,
            customSeedColor = prefs[Keys.CUSTOM_SEED_COLOR]?.let { Color(it) } ?: OrangeSeed.Primary,
            homeSections = parseHomeSections(prefs[Keys.HOME_SECTIONS]),
            // BLURRED_ARTWORK is hidden from the picker (kept working in code, just not offered as a
            // choice) - coerce anyone still holding it from before back to the default.
            playerBackgroundStyle = (prefs[Keys.PLAYER_BACKGROUND_STYLE]?.let {
                runCatching { PlayerBackgroundStyle.valueOf(it) }.getOrNull()
            } ?: PlayerBackgroundStyle.ORB).let {
                if (it == PlayerBackgroundStyle.BLURRED_ARTWORK) PlayerBackgroundStyle.ORB else it
            },
            playerStyle = prefs[Keys.PLAYER_STYLE]?.let {
                runCatching { PlayerStyle.valueOf(it) }.getOrNull()
            } ?: PlayerStyle.PIXEL,
            backgroundMode = prefs[Keys.BACKGROUND_MODE]?.let {
                runCatching { AppBackgroundMode.valueOf(it) }.getOrNull()
            } ?: AppBackgroundMode.DYNAMIC,
            backgroundCustomColor = prefs[Keys.BACKGROUND_CUSTOM_COLOR]?.let { Color(it) } ?: Color(0xFF1B1B1F),
            playerHintShown = prefs[Keys.PLAYER_HINT_SHOWN] ?: false,
            pixelGlowEnabled = prefs[Keys.PIXEL_GLOW_ENABLED] ?: true,
            cacheLimitMb = prefs[Keys.CACHE_LIMIT_MB] ?: DefaultCacheLimitMb,
            crossfadeSeconds = (prefs[Keys.CROSSFADE_SECONDS] ?: DefaultCrossfadeSeconds)
                .coerceIn(MinCrossfadeSeconds, MaxCrossfadeSeconds),
            dividerStyle = prefs[Keys.DIVIDER_STYLE]?.let {
                runCatching { DividerStyle.valueOf(it) }.getOrNull()
            } ?: DividerStyle.SUBTLE,
        )
    }

    val autoplayNext = context.dataStore.data.map { it[Keys.AUTOPLAY_NEXT] ?: true }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit {
            it[Keys.CROSSFADE_SECONDS] = seconds.coerceIn(MinCrossfadeSeconds, MaxCrossfadeSeconds)
        }
    }

    suspend fun setCacheLimitMb(limitMb: Int) {
        context.dataStore.edit { it[Keys.CACHE_LIMIT_MB] = limitMb }
    }

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

    suspend fun setLyricsProvider(provider: LyricsProvider) {
        context.dataStore.edit { it[Keys.LYRICS_PROVIDER] = provider.name }
    }

    suspend fun setGeniusFallbackEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.GENIUS_FALLBACK_ENABLED] = value }
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

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }

    suspend fun setEulaAccepted(value: Boolean) {
        context.dataStore.edit { it[Keys.EULA_ACCEPTED] = value }
    }

    suspend fun setCrossfadeEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.CROSSFADE_ENABLED] = value }
    }

    suspend fun setSeekBarStyle(style: SeekBarStyle) {
        context.dataStore.edit { it[Keys.SEEK_BAR_STYLE] = style.name }
    }

    suspend fun setCustomSeedColor(color: Color) {
        context.dataStore.edit { it[Keys.CUSTOM_SEED_COLOR] = color.toArgb() }
    }

    suspend fun setHomeSections(sections: List<HomeSectionConfig>) {
        context.dataStore.edit { it[Keys.HOME_SECTIONS] = serializeHomeSections(sections) }
    }

    suspend fun setPlayerBackgroundStyle(style: PlayerBackgroundStyle) {
        context.dataStore.edit { it[Keys.PLAYER_BACKGROUND_STYLE] = style.name }
    }

    suspend fun setPlayerStyle(style: PlayerStyle) {
        context.dataStore.edit { it[Keys.PLAYER_STYLE] = style.name }
    }

    suspend fun setBackgroundMode(mode: AppBackgroundMode) {
        context.dataStore.edit { it[Keys.BACKGROUND_MODE] = mode.name }
    }

    suspend fun setBackgroundCustomColor(color: Color) {
        context.dataStore.edit { it[Keys.BACKGROUND_CUSTOM_COLOR] = color.toArgb() }
    }

    suspend fun setPlayerHintShown(value: Boolean) {
        context.dataStore.edit { it[Keys.PLAYER_HINT_SHOWN] = value }
    }

    suspend fun setPixelGlowEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.PIXEL_GLOW_ENABLED] = value }
    }

    suspend fun setDividerStyle(style: DividerStyle) {
        context.dataStore.edit { it[Keys.DIVIDER_STYLE] = style.name }
    }
}
