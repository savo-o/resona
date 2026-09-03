package com.savoo.scclient

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.debug.CrashReporter
import com.savoo.scclient.debug.ScreenshotModeInterceptor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SCApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var clientIdProvider: ClientIdProvider

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        CrashReporter.install(this)
        super.onCreate()
        appScope.launch {
            if (clientIdProvider.cachedOrFallback().isBlank()) {
                clientIdProvider.refresh()
            }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(ScreenshotModeInterceptor()) }
        .build()
}
