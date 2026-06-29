package com.savoo.scclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.data.repository.AppSettings
import com.savoo.scclient.data.repository.DarkModeOption
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.navigation.RootScreen
import com.savoo.scclient.ui.theme.AppColorTheme
import com.savoo.scclient.ui.theme.ResonaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playerController: PlayerController

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

            ResonaTheme(
                colorTheme = effectiveTheme,
                darkTheme = isDark,
                overrideSeedColor = if (settings.dynamicFromTrack) trackSeedColor else null,
            ) {
                RootScreen()
            }
        }
    }

    override fun onDestroy() {
        playerController.saveState()
        super.onDestroy()
    }
}
