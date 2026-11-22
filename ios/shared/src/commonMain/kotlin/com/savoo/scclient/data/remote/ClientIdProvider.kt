package com.savoo.scclient.data.remote

import com.savoo.scclient.compat.SharedPreferences
import com.savoo.scclient.compat.BuildConfig
import kotlin.concurrent.Volatile
import kotlinx.coroutines.IO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.savoo.scclient.compat.http.OkHttpClient
import com.savoo.scclient.compat.http.Request

class ClientIdProvider(
    private val prefs: SharedPreferences,
    private val plainClient: OkHttpClient,
) {
    private val mutex = Mutex()
    @Volatile private var lastRefreshAttemptAt = 0L
    private var lastRefreshSucceeded = false

    companion object {
        private const val KEY_CLIENT_ID = "sc_client_id"
        private const val MIN_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        private val CLIENT_ID_REGEX = Regex("""["']?client_id["']?\s*[=:]\s*["']([a-zA-Z0-9]{32})["']""")
    }

    fun cachedOrFallback(): String =
        prefs.getString(KEY_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SC_CLIENT_ID_FALLBACK

    fun setManualOverride(id: String) {
        prefs.edit().putString(KEY_CLIENT_ID, id).apply()
    }

    suspend fun clear() = mutex.withLock {
        prefs.edit().remove(KEY_CLIENT_ID).apply()
        lastRefreshAttemptAt = 0L
        lastRefreshSucceeded = false
    }

    suspend fun refresh(force: Boolean = false, failOnError: Boolean = false): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = com.savoo.scclient.compat.currentTimeMillis()
            val interval = if (lastRefreshSucceeded) MIN_REFRESH_INTERVAL_MS else 30000L
            if (!force && lastRefreshAttemptAt != 0L && now - lastRefreshAttemptAt < interval) {
                return@withLock cachedOrFallback()
            }
            lastRefreshAttemptAt = now
            lastRefreshSucceeded = false
            val result = runCatching {
                val homepage = plainClient.newCall(
                    Request.Builder().url("https://soundcloud.com").header("User-Agent", BROWSER_USER_AGENT).header("Cache-Control", "no-cache").build()
                ).execute().use {
                    if (!it.isSuccessful) error("SoundCloud homepage failed: ${it.code}")
                    it.body?.string().orEmpty()
                }

                val hydratedId = soundCloudHydration(homepage)
                    .firstOrNull { it.optString("hydratable") == "apiClient" }
                    ?.optJSONObject("data")?.optString("id")
                    ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{32}")) }
                if (hydratedId != null) {
                    prefs.edit().putString(KEY_CLIENT_ID, hydratedId).apply()
                    lastRefreshSucceeded = true
                    return@runCatching hydratedId
                }

                val scriptUrls = Ksoup.parse(homepage, "https://soundcloud.com/").select("script[src]")
                    .map { it.absUrl("src") }
                    .filter { it.startsWith("https://a-v2.sndcdn.com/assets/") && it.substringBefore('?').endsWith(".js") }
                    .distinct()

                for (scriptUrl in scriptUrls.asReversed()) {
                    val js = runCatching {
                        plainClient.newCall(Request.Builder().url(scriptUrl).header("User-Agent", BROWSER_USER_AGENT).build())
                            .execute().use { if (it.isSuccessful) it.body?.string().orEmpty() else "" }
                    }.getOrDefault("")
                    val match = CLIENT_ID_REGEX.find(js)
                    if (match != null) {
                        val id = match.groupValues[1]
                        prefs.edit().putString(KEY_CLIENT_ID, id).apply()
                        lastRefreshSucceeded = true
                        return@runCatching id
                    }
                }
                error("Client ID not found")
            }
            if (failOnError) result.getOrThrow() else result.getOrElse { cachedOrFallback() }
        }
    }
}
