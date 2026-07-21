package com.savoo.scclient

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
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

    private var deepLinkTarget by mutableStateOf<DeepLinkTarget>(DeepLinkTarget.None)

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

        deepLinkTarget = DeepLinkTarget.fromIntent(intent)

        val currentLang = run {
            val prefs = getSharedPreferences("sc_settings", MODE_PRIVATE)
            prefs.getString("language", null)?.let {
                runCatching { LanguageOption.valueOf(it) }.getOrNull()
            } ?: LanguageOption.ENGLISH
        }

        val darkMode = run {
            val prefs = getSharedPreferences("sc_settings", MODE_PRIVATE)
            prefs.getString("dark_mode", null)?.let {
                runCatching { DarkModeOption.valueOf(it) }.getOrNull()
            } ?: DarkModeOption.SYSTEM
        }

        val isDark = when (darkMode) {
            DarkModeOption.LIGHT -> false
            DarkModeOption.DARK -> true
            DarkModeOption.SYSTEM -> resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        if (isDark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            )
        }

        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val trackSeedColor by playerController.seedColor.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDark = when (settings.darkMode) {
                DarkModeOption.SYSTEM -> systemDark
                DarkModeOption.LIGHT -> false
                DarkModeOption.DARK -> true
            }

            LaunchedEffect(isDark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }

            val effectiveTheme = if (settings.dynamicFromTrack && trackSeedColor != null) {
                AppColorTheme.DYNAMIC_TRACK
            } else {
                settings.colorTheme
            }

            ResonaTheme(
                colorTheme = effectiveTheme,
                darkTheme = isDark,
                overrideSeedColor = if (settings.dynamicFromTrack) trackSeedColor else null,
            ) {
                RootScreen(initialDeepLink = deepLinkTarget)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask means a deep link tapped while the app is already running arrives here, not in onCreate.
        setIntent(intent)
        deepLinkTarget = DeepLinkTarget.fromIntent(intent)
    }

    override fun onDestroy() {
        playerController.saveState()
        super.onDestroy()
    }
}
