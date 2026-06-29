package com.savoo.scclient.data.remote

import android.content.SharedPreferences
import com.savoo.scclient.BuildConfig
import com.savoo.scclient.di.PlainHttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientIdProvider @Inject constructor(
    private val prefs: SharedPreferences,
    @PlainHttpClient private val plainClient: OkHttpClient,
) {
    private val mutex = Mutex()

    companion object {
        private const val KEY_CLIENT_ID = "sc_client_id"
        private val APP_JS_REGEX = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
        private val CLIENT_ID_REGEX = Regex("""client_id[=:]"?([a-zA-Z0-9]{32})"?""")
    }

    fun cachedOrFallback(): String =
        prefs.getString(KEY_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SC_CLIENT_ID_FALLBACK

    fun setManualOverride(id: String) {
        prefs.edit().putString(KEY_CLIENT_ID, id).apply()
    }

    suspend fun refresh(): String = mutex.withLock {
        runCatching {
            val homepage = plainClient.newCall(
                Request.Builder().url("https://soundcloud.com").build()
            ).execute().use { it.body?.string().orEmpty() }

            val scriptUrls = APP_JS_REGEX.findAll(homepage).map { it.groupValues[1] }.toList()

            for (scriptUrl in scriptUrls.asReversed()) {
                val js = plainClient.newCall(Request.Builder().url(scriptUrl).build())
                    .execute().use { it.body?.string().orEmpty() }
                val match = CLIENT_ID_REGEX.find(js)
                if (match != null) {
                    val id = match.groupValues[1]
                    prefs.edit().putString(KEY_CLIENT_ID, id).apply()
                    return@runCatching id
                }
            }
            null
        }.getOrNull() ?: cachedOrFallback()
    }
}
