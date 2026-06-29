package com.savoo.scclient

import android.app.Application
import com.savoo.scclient.data.remote.ClientIdProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SCApplication : Application() {

    @Inject lateinit var clientIdProvider: ClientIdProvider

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            if (clientIdProvider.cachedOrFallback().isBlank()) {
                clientIdProvider.refresh()
            }
        }
    }
}
