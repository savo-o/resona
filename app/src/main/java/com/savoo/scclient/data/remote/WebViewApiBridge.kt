package com.savoo.scclient.data.remote

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.savoo.scclient.auth.TokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs like/unlike through a real (headless) WebView's `fetch()` instead of a plain HTTP client,
 * since SoundCloud's edge protection (DataDome) blocks the "create a like" write from a bare
 * OkHttp client even with browser-shaped headers, but allows it from an actual browser/WebView
 * JS engine's own fetch. Must stay attached to a window (see MainActivity) - Android throttles
 * JS timers on unattached WebViews, which otherwise makes calls fail unpredictably.
 */
@Singleton
class WebViewApiBridge @Inject constructor(
    private val clientIdProvider: ClientIdProvider,
    private val tokenStore: TokenStore,
) {
    private var webView: WebView? = null
    private var pageReady = CompletableDeferred<Unit>()
    private val callMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var nextCallId = 0

    @Volatile
    var lastResponseBody: String? = null
        private set

    @Volatile
    var lastResponsePayload: org.json.JSONObject? = null
        private set

    @Volatile
    private var cachedAppVersion: String? = null

    private val TAG = "WebViewApiBridge"

    fun setWebView(wv: WebView) {
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.javaScriptCanOpenWindowsAutomatically = false
        wv.settings.setSupportMultipleWindows(false)
        wv.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        webView = wv
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                Log.d(TAG, "page loaded: $url")
                if (!pageReady.isCompleted) pageReady.complete(Unit)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                val allowed = host == "soundcloud.com" || host.endsWith(".soundcloud.com")
                if (!allowed) Log.w(TAG, "blocked navigation to $host")
                return !allowed
            }
        }
        syncCookiesToWebView()
        wv.loadUrl("https://soundcloud.com/discover")

        // This page loads once at cold start, usually before the user has logged in. Reload it
        // (with freshly-synced cookies) whenever login state changes to true, so the bridge's
        // fetches run in an authenticated page context instead of being stuck on the pre-login
        // session for the rest of the process's lifetime.
        tokenStore.isLoggedIn
            .onEach { loggedIn ->
                if (loggedIn) {
                    pageReady = CompletableDeferred()
                    syncCookiesToWebView()
                    webView?.reload()
                }
            }
            .launchIn(scope)
    }

    private fun syncCookiesToWebView() {
        val cookies = tokenStore.webCookies ?: return
        val cookieManager = CookieManager.getInstance()
        cookies.split(";").forEach { pair ->
            val trimmed = pair.trim()
            if (trimmed.isNotEmpty()) {
                cookieManager.setCookie("https://soundcloud.com", trimmed)
            }
        }
        cookieManager.flush()
    }

    // Returns the real HTTP status code (0 if the request never completed at all - timeout/JS
    // error/no page) so callers can tell "404, already in the desired state" apart from a real
    // failure, rather than collapsing everything into a single success/failure bit.
    suspend fun likeTrack(userId: Long, trackId: Long): Int = executeFetch(
        method = "PUT",
        url = "https://api-v2.soundcloud.com/users/$userId/track_likes/$trackId"
    )

    suspend fun unlikeTrack(userId: Long, trackId: Long): Int = executeFetch(
        method = "DELETE",
        url = "https://api-v2.soundcloud.com/users/$userId/track_likes/$trackId"
    )

    suspend fun deleteComment(commentId: Long): Int = executeFetch(
        method = "DELETE",
        url = "https://api-v2.soundcloud.com/comments/$commentId",
    )

    suspend fun postComment(trackId: Long, body: String, timestampMs: Long): Int {
        val query = "body=" + java.net.URLEncoder.encode(body, "UTF-8") + "&timestamp=$timestampMs"
        return executeFetch(
            method = "POST",
            url = "https://api-v2.soundcloud.com/tracks/$trackId/comments",
            extraQuery = query,
        )
    }

    // Serialized (callMutex) so two near-simultaneous taps can't clobber each other's result via
    // the shared window state, and polled (not a fixed delay) so a slow-but-successful request
    // isn't misread as a failure.
    private suspend fun executeFetch(
        method: String,
        url: String,
        jsonBody: String? = null,
        contentType: String = "application/json",
        extraQuery: String? = null,
    ): Int = callMutex.withLock {
        val wv = webView ?: return@withLock 0
        if (withTimeoutOrNull(8000) { pageReady.await() } == null) {
            Log.w(TAG, "$method $url: page never became ready")
            return@withLock 0
        }

        val clientId = clientIdProvider.cachedOrFallback()
        val token = tokenStore.accessToken.orEmpty()
        val version = appVersion()
        val fullUrl = buildString {
            append(url)
            append("?client_id=").append(clientId)
            if (version != null) append("&app_version=").append(version)
            append("&app_locale=en")
            if (extraQuery != null) append("&").append(extraQuery)
        }
        val slot = "__bridgeCall${nextCallId++}"

        val headersJs = if (jsonBody == null) {
            "{ 'Authorization': ${org.json.JSONObject.quote("OAuth $token")} }"
        } else {
            "{ 'Authorization': ${org.json.JSONObject.quote("OAuth $token")}, 'Content-Type': ${org.json.JSONObject.quote(contentType)} }"
        }
        val bodyJs = if (jsonBody == null) "" else ", body: ${org.json.JSONObject.quote(jsonBody)}"

        val js = """
            (function() {
                window.$slot = { done: false, result: null };
                fetch(${org.json.JSONObject.quote(fullUrl)}, {
                    method: ${org.json.JSONObject.quote(method)},
                    credentials: 'include',
                    headers: $headersJs$bodyJs
                }).then(function(resp) {
                    return resp.text().then(function(body) {
                        window.$slot.result = JSON.stringify({code: resp.status, ok: resp.ok, body: body});
                        window.$slot.done = true;
                    });
                }).catch(function(e) {
                    window.$slot.result = JSON.stringify({code: 0, ok: false, error: e.message});
                    window.$slot.done = true;
                });
            })()
        """.trimIndent()

        withContext(Dispatchers.Main) { wv.evaluateJavascript(js, null) }

        val readJs = "(function() { var s = window.$slot; return (s && s.done) ? s.result : null; })()"
        val raw = withTimeoutOrNull(10_000) {
            var result: String? = null
            while (result == null) {
                result = evalOnMain(wv, readJs)
                if (result == null) delay(250)
            }
            result
        }
        // Clean up the per-call slot so it doesn't accumulate on the page's window object.
        withContext(Dispatchers.Main) { wv.evaluateJavascript("delete window.$slot;", null) }

        val decoded = runCatching { org.json.JSONTokener(raw.orEmpty()).nextValue() as? String }.getOrNull()
            ?: raw?.removeSurrounding("\"")?.replace("\\\"", "\"")
        lastResponseBody = decoded
        lastResponsePayload = runCatching {
            org.json.JSONObject(org.json.JSONObject(decoded.orEmpty()).getString("body"))
        }.getOrNull()
        Log.d(TAG, "$method $url -> $decoded")
        Regex("\"code\":(\\d+)").find(decoded.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private suspend fun appVersion(): String? {
        cachedAppVersion?.let { return it }
        val wv = webView ?: return null
        val raw = evalOnMain(wv, "(function() { return window.__sc_version || (window.webpackJsonp && window.__sc_version) || null; })()")
        val value = raw?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "null" }
        Log.d(TAG, "app_version from page: $value")
        cachedAppVersion = value
        return value
    }

    private suspend fun evalOnMain(wv: WebView, js: String): String? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String?>()
        wv.evaluateJavascript(js) { result -> deferred.complete(result?.takeIf { it != "null" }) }
        deferred.await()
    }
}
