package com.savoo.scclient

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.data.repository.DarkModeOption
import com.savoo.scclient.data.repository.LanguageOption
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.systemDefaultLanguage
import com.savoo.scclient.i18n.withCustomStrings
import com.savoo.scclient.data.remote.WebViewApiBridge
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.navigation.DeepLinkTarget
import com.savoo.scclient.ui.navigation.RootScreen
import com.savoo.scclient.ui.screens.onboarding.EulaGateScreen
import com.savoo.scclient.ui.screens.onboarding.OnboardingScreen
import com.savoo.scclient.ui.theme.AppColorTheme
import com.savoo.scclient.ui.theme.ResonaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playerController: PlayerController
    @Inject lateinit var webBridge: WebViewApiBridge

    private var deepLinkTarget by mutableStateOf<DeepLinkTarget>(DeepLinkTarget.None)
    private var apiWebView: WebView? = null

    override fun attachBaseContext(newBase: Context) {
        val lang = try {
            val prefs = newBase.getSharedPreferences("sc_settings", MODE_PRIVATE)
            prefs.getString("language", null)?.let {
                runCatching { LanguageOption.valueOf(it) }.getOrNull()
            } ?: systemDefaultLanguage(newBase)
        } catch (_: Exception) {
            LanguageOption.ENGLISH
        }

        val locale = lang.locale ?: Locale.getDefault()
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val updatedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(updatedContext.withCustomStrings())
    }

    private fun applyOrientationLock() {
        requestedOrientation = if (resources.getBoolean(R.bool.lock_portrait)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyOrientationLock()

        apiWebView = WebView(this).apply {
            // Must be attached to a real window (not just held in a field) - Android throttles
            // JS timers/rendering ticks on WebViews that are never added to the view hierarchy,
            // which otherwise makes the WebViewApiBridge's fetch calls succeed inconsistently.
            // 1x1 + alpha 0 keeps it invisible without making it inactive (unlike GONE/detached).
            alpha = 0f
            addContentView(this, android.view.ViewGroup.LayoutParams(1, 1))
            webBridge.setWebView(this)
        }

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
            val settings = settingsRepository.settings.collectAsState(initial = null).value
            val trackSeedColor by playerController.seedColor.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

            if (settings == null) {
                androidx.compose.material3.Surface(
                    color = if (systemDark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
                ) {}
                return@setContent
            }

            val isDark = when (settings.darkMode) {
                DarkModeOption.SYSTEM -> systemDark
                DarkModeOption.LIGHT -> false
                DarkModeOption.DARK -> true
            }

            val backgroundMode = settings.backgroundMode
            val pixelWash = backgroundMode == com.savoo.scclient.data.repository.AppBackgroundMode.DYNAMIC &&
                trackSeedColor != null
            val customBackground = settings.backgroundCustomColor
                .takeIf { backgroundMode == com.savoo.scclient.data.repository.AppBackgroundMode.CUSTOM }

            LaunchedEffect(isDark, pixelWash, customBackground) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                val lightBars = when {
                    pixelWash -> false
                    customBackground != null -> customBackground.luminance() > 0.5f
                    else -> !isDark
                }
                controller.isAppearanceLightStatusBars = lightBars
                controller.isAppearanceLightNavigationBars = lightBars
            }

            val effectiveTheme = if (settings.dynamicFromTrack && trackSeedColor != null) {
                AppColorTheme.DYNAMIC_TRACK
            } else {
                settings.colorTheme
            }

            ResonaTheme(
                colorTheme = effectiveTheme,
                darkTheme = isDark,
                overrideSeedColor = when {
                    settings.dynamicFromTrack -> trackSeedColor
                    settings.colorTheme == AppColorTheme.CUSTOM -> settings.customSeedColor
                    else -> null
                },
                tintSurfaces = pixelWash,
                customBackground = customBackground,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.savoo.scclient.ui.haptics.LocalHapticsEnabled provides settings.hapticsEnabled,
                    com.savoo.scclient.ui.haptics.LocalHapticsIntensity provides settings.hapticsIntensity,
                    com.savoo.scclient.ui.components.LocalDividerStyle provides settings.dividerStyle,
                ) {
                    var eulaDismissed by androidx.compose.runtime.remember { mutableStateOf(false) }
                    var onboardingDismissed by androidx.compose.runtime.remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    when {
                        !settings.eulaAccepted && !eulaDismissed -> {
                            EulaGateScreen(
                                onAccept = {
                                    eulaDismissed = true
                                    scope.launch { settingsRepository.setEulaAccepted(true) }
                                },
                                onDecline = { finish() },
                            )
                        }
                        !settings.onboardingCompleted && !onboardingDismissed -> {
                            OnboardingScreen(
                                onFinish = {
                                    onboardingDismissed = true
                                    scope.launch { settingsRepository.setOnboardingCompleted(true) }
                                },
                            )
                        }
                        else -> RootScreen(initialDeepLink = deepLinkTarget)
                    }
                }
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
        apiWebView?.destroy()
        apiWebView = null
        playerController.saveState()
        super.onDestroy()
    }
}
