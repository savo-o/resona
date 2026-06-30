package com.savoo.scclient

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.data.repository.AppSettings
import com.savoo.scclient.data.repository.DarkModeOption
import com.savoo.scclient.data.repository.LanguageOption
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.navigation.DeepLinkTarget
import com.savoo.scclient.ui.navigation.RootScreen
import com.savoo.scclient.ui.theme.AppColorTheme
import com.savoo.scclient.ui.theme.ResonaTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playerController: PlayerController

    override fun attachBaseContext(newBase: Context) {
        val lang = try {
            val prefs = newBase.getSharedPreferences("sc_settings", MODE_PRIVATE)
            prefs.getString("language", null)?.let {
                runCatching { LanguageOption.valueOf(it) }.getOrNull()
            } ?: LanguageOption.ENGLISH
        } catch (_: Exception) {
            LanguageOption.ENGLISH
        }

        val locale = lang.locale ?: Locale.getDefault()
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val updatedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(updatedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val trackSeedColor by playerController.seedColor.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDark = when (settings.darkMode) {
                DarkModeOption.SYSTEM -> systemDark
                DarkModeOption.LIGHT -> false
                DarkModeOption.DARK -> true
            }

            val effectiveTheme = if (settings.dynamicFromTrack && trackSeedColor != null) {
                AppColorTheme.DYNAMIC_TRACK
            } else {
                settings.colorTheme
            }

            val deepLinkTarget = remember { parseDeepLink(intent) }

            ResonaTheme(
                colorTheme = effectiveTheme,
                darkTheme = isDark,
                overrideSeedColor = if (settings.dynamicFromTrack) trackSeedColor else null,
            ) {
                RootScreen(initialDeepLink = deepLinkTarget)
            }
        }
    }

    private fun parseDeepLink(intent: Intent): DeepLinkTarget? {
        val data = intent.data ?: return null
        val host = data.host ?: return null
        val path = data.pathSegments ?: return null

        if (host != "soundcloud.com" && host != "www.soundcloud.com") return null
        if (path.isEmpty()) return null

        return when (path[0]) {
            "users" -> {
                val userId = path.getOrNull(1)?.toLongOrNull()
                if (userId != null) DeepLinkTarget.Artist(userId) else null
            }
            "playlists" -> {
                val playlistId = path.getOrNull(1)?.toLongOrNull()
                if (playlistId != null) DeepLinkTarget.Playlist(playlistId) else null
            }
            else -> null
        }
    }

    override fun onDestroy() {
        playerController.saveState()
        super.onDestroy()
    }
}
