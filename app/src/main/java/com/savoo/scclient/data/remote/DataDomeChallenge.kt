package com.savoo.scclient.data.remote

import android.os.SystemClock
import android.webkit.CookieManager
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.debug.DebugLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class PendingChallenge(val url: String, val hardBlock: Boolean)

@Singleton
class DataDomeChallenge @Inject constructor(
    private val tokenStore: TokenStore,
) {
    private val _pending = MutableStateFlow<PendingChallenge?>(null)
    val pending = _pending.asStateFlow()

    private val lock = Any()
    private var result: CompletableDeferred<Boolean>? = null
    private var dismissedAt = 0L

    suspend fun solve(url: String): Boolean {
        val deferred = synchronized(lock) {
            if (SystemClock.elapsedRealtime() - dismissedAt < DISMISS_COOLDOWN_MS) return false
            result?.takeIf { it.isActive } ?: CompletableDeferred<Boolean>().also {
                result = it
                _pending.value = PendingChallenge(url, hardBlock = isHardBlock(url))
                DebugLog.log(TAG, "challenge shown: ${url.take(120)}")
            }
        }
        val passed = withTimeoutOrNull(WAIT_TIMEOUT_MS) { deferred.await() } ?: false
        synchronized(lock) {
            if (result === deferred && deferred.isActive) {
                deferred.complete(false)
                _pending.value = null
            }
        }
        return passed
    }

    suspend fun onPassed(cookie: String) {
        val pair = cookie.substringBefore(";").trim()
        if (!pair.startsWith("datadome=")) return
        withContext(Dispatchers.Main) {
            CookieManager.getInstance().apply {
                setCookie("https://soundcloud.com", cookie)
                flush()
            }
        }
        tokenStore.replaceCookie(pair)
        DebugLog.log(TAG, "challenge passed")
        finish(true)
    }

    fun onDismissed() {
        synchronized(lock) { dismissedAt = SystemClock.elapsedRealtime() }
        DebugLog.log(TAG, "challenge dismissed")
        finish(false)
    }

    private fun finish(passed: Boolean) {
        synchronized(lock) {
            result?.complete(passed)
            result = null
            _pending.value = null
        }
    }

    companion object {
        private const val TAG = "DataDome"
        private const val WAIT_TIMEOUT_MS = 3 * 60_000L
        private const val DISMISS_COOLDOWN_MS = 60_000L

        fun challengeUrl(code: Int, body: String?): String? {
            if (code != 403 || body == null || "captcha-delivery.com" !in body) return null
            return runCatching { JSONObject(body).optString("url") }.getOrNull()
                ?.takeIf { it.startsWith("https://") && "captcha-delivery.com" in it }
        }

        private fun isHardBlock(url: String): Boolean =
            android.net.Uri.parse(url).getQueryParameter("t") == "bv"
    }
}
