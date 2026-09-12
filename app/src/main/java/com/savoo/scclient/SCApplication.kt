package com.savoo.scclient

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.debug.CrashReporter
import com.savoo.scclient.debug.ScreenshotModeInterceptor
import com.savoo.scclient.ui.navigation.AppShortcuts
import com.savoo.scclient.work.AutoExportManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SCApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var clientIdProvider: ClientIdProvider
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var autoExportManager: AutoExportManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        CrashReporter.install(this)
        super.onCreate()
        appScope.launch {
            if (clientIdProvider.cachedOrFallback().isBlank()) {
                clientIdProvider.refresh()
            }
        }
        appScope.launch { AppShortcuts.install(this@SCApplication) }
        appScope.launch {
            autoExportManager.syncSchedule()
            autoExportManager.catchUpIfDue()
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(ScreenshotModeInterceptor()) }
        .build()
}
