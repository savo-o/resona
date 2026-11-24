package com.savoo.scclient.data.remote

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.debug.DebugLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    private val challenge: DataDomeChallenge,
) {
    private var webView: WebView? = null
    private var pageGeneration = 0
    private val callMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var nextCallId = 0
    private var staleSessionChecked = false
    private var loginObserver: kotlinx.coroutines.Job? = null

    private var cachedAppVersion: String? = null

    private val TAG = "WebViewApiBridge"

    fun setWebView(wv: WebView) {
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.javaScriptCanOpenWindowsAutomatically = false
        wv.settings.setSupportMultipleWindows(false)
        wv.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        webView = wv
        pageGeneration++
        cachedAppVersion = null
        loginObserver?.cancel()
        if (!staleSessionChecked) {
            staleSessionChecked = true
            if (!tokenStore.isLoggedIn.value) {
                runCatching {
                    CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.WebStorage.getInstance().deleteAllData()
                }
                DebugLog.log(TAG, "signed out at start: cleared leftover web session")
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (view !== webView) return
                pageGeneration++
                cachedAppVersion = null
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (view === webView) {
                    DebugLog.log(TAG, "bridge page finished: ${url?.let { Uri.parse(it).host }}")
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (view === webView && request.isForMainFrame) {
                    DebugLog.log(TAG, "bridge page error: ${request.url.host}, WebView ${error.errorCode}")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (view === webView && request.isForMainFrame) {
                    DebugLog.log(TAG, "bridge page error: ${request.url.host}, HTTP ${errorResponse.statusCode}")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                val allowed = host == "soundcloud.com" || host.endsWith(".soundcloud.com") ||
                    host.endsWith(".captcha-delivery.com")
                if (!allowed) Log.w(TAG, "blocked navigation to $host")
                return !allowed
            }
        }
        syncCookiesToWebView()
        wv.loadUrl(BRIDGE_PAGE_URL)

        // This page loads once at cold start, usually before the user has logged in. Reload it
        // (with freshly-synced cookies) whenever login state changes to true, so the bridge's
        // fetches run in an authenticated page context instead of being stuck on the pre-login
        // session for the rest of the process's lifetime.
        var wasLoggedIn = tokenStore.isLoggedIn.value
        loginObserver = tokenStore.isLoggedIn
            .onEach { loggedIn ->
                val justLoggedIn = loggedIn && !wasLoggedIn
                wasLoggedIn = loggedIn
                if (justLoggedIn) callMutex.withLock {
                    if (webView !== wv || !tokenStore.isLoggedIn.value) return@withLock
                    pageGeneration++
                    cachedAppVersion = null
                    syncCookiesToWebView()
                    wv.loadUrl(BRIDGE_PAGE_URL)
                }
            }
            .launchIn(scope)
    }

    fun clearWebView(view: WebView) {
        if (webView !== view) return
        webView = null
        pageGeneration++
        loginObserver?.cancel()
        loginObserver = null
        cachedAppVersion = null
    }

    private fun syncCookiesToWebView() {
        val cookies = tokenStore.webCookies ?: return
        val cookieManager = CookieManager.getInstance()
        cookies.split(";").forEach { pair ->
            val trimmed = pair.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("datadome=")) {
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

    suspend fun likeTrackResponse(userId: Long, trackId: Long): BridgeResponse = executeRequest(
        method = "PUT",
        url = "https://api-v2.soundcloud.com/users/$userId/track_likes/$trackId",
        solveChallenge = false,
    )

    suspend fun ackSpamWarning(urn: String): BridgeResponse = executeRequest(
        method = "POST",
        url = "https://api-v2.soundcloud.com/me/spam_warnings/${android.net.Uri.encode(urn)}/ack",
    )

    suspend fun unlikeTrack(userId: Long, trackId: Long): Int = executeFetch(
        method = "DELETE",
        url = "https://api-v2.soundcloud.com/users/$userId/track_likes/$trackId"
    )

    suspend fun deleteComment(commentId: Long): Int = executeFetch(
        method = "DELETE",
        url = "https://api-v2.soundcloud.com/comments/$commentId",
    )

    suspend fun postComment(trackId: Long, body: String, timestampMs: Long): BridgeResponse {
        val query = "body=" + java.net.URLEncoder.encode(body, "UTF-8") + "&timestamp=$timestampMs"
        return executeRequest(
            method = "POST",
            url = "https://api-v2.soundcloud.com/tracks/$trackId/comments",
            extraQuery = query,
        )
    }

    suspend fun updateMe(json: String): BridgeResponse = executeRequest(
        method = "PUT",
        url = "https://api-v2.soundcloud.com/me",
        jsonBody = json,
    )

    suspend fun uploadAvatar(base64: String): BridgeResponse = executeRequest(
        method = "PUT",
        url = "https://api-v2.soundcloud.com/me/profile/avatar",
        jsonBody = org.json.JSONObject().put("image_data", base64).toString(),
        timeoutMs = 45_000,
    )

    suspend fun deleteAvatar(): BridgeResponse = executeRequest(
        method = "DELETE",
        url = "https://api-v2.soundcloud.com/me/profile/avatar",
    )

    suspend fun createPlaylist(json: String): BridgeResponse = executeRequest(
        method = "POST",
        url = "https://api-v2.soundcloud.com/playlists",
        jsonBody = json,
    )

    suspend fun updatePlaylist(playlistId: Long, json: String): BridgeResponse = executeRequest(
        method = "PUT",
        url = "https://api-v2.soundcloud.com/playlists/$playlistId",
        jsonBody = json,
    )

    suspend fun deletePlaylist(playlistId: Long): BridgeResponse = executeRequest(
        method = "DELETE",
        url = "https://api-v2.soundcloud.com/playlists/$playlistId",
    )

    private suspend fun executeFetch(
        method: String,
        url: String,
        jsonBody: String? = null,
        contentType: String = "application/json",
        extraQuery: String? = null,
    ): Int = executeRequest(method, url, jsonBody, contentType, extraQuery).code

    // Serialized (callMutex) so two near-simultaneous taps can't clobber each other's result via
    // the shared window state, and polled (not a fixed delay) so a slow-but-successful request
    // isn't misread as a failure.
    private suspend fun executeRequest(
        method: String,
        url: String,
        jsonBody: String? = null,
        contentType: String = "application/json",
        extraQuery: String? = null,
        timeoutMs: Long = 10_000,
        solveChallenge: Boolean = true,
    ): BridgeResponse {
        val first = executeOnce(method, url, jsonBody, contentType, extraQuery, timeoutMs)
        if (method != "GET") challenge.recordWrite(first.code, first.body, afterSolve = false)
        if (!solveChallenge) return first
        val challengeUrl = DataDomeChallenge.challengeUrl(first.code, first.body) ?: return first
        if (!challenge.solve(challengeUrl)) return first
        val retried = executeOnce(method, url, jsonBody, contentType, extraQuery, timeoutMs)
        if (method != "GET") challenge.recordWrite(retried.code, retried.body, afterSolve = true)
        return retried
    }

    private suspend fun executeOnce(
        method: String,
        url: String,
        jsonBody: String?,
        contentType: String,
        extraQuery: String?,
        timeoutMs: Long,
    ): BridgeResponse = withContext(Dispatchers.Main) {
        val queuedAt = android.os.SystemClock.elapsedRealtime()
        callMutex.withLock {
            val lockedAt = android.os.SystemClock.elapsedRealtime()
            val wv = webView ?: return@withLock BridgeResponse(0, null)
            val generation = withTimeoutOrNull(8000) { awaitPage(wv) }
            if (generation == null) {
                val host = wv.url?.let { Uri.parse(it).host }
                DebugLog.log(TAG, "$method ${url.substringAfter("soundcloud.com")}: bridge page unavailable, host=$host")
                return@withLock BridgeResponse(0, null)
            }
            val readyAt = android.os.SystemClock.elapsedRealtime()

            val version = appVersion(wv, generation)
            if (webView !== wv || pageGeneration != generation) return@withLock BridgeResponse(0, null)
            val clientId = clientIdProvider.cachedOrFallback()
            val token = tokenStore.accessToken.orEmpty()
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

            val js = """
                (function() {
                    window.$slot = { done: false, result: null };
                    if (!($PAGE_READY_JS)) {
                        window.$slot.result = JSON.stringify({code: 0, error: 'page_unavailable'});
                        window.$slot.done = true;
                        return;
                    }
                    try {
                        var xhr = new XMLHttpRequest();
                        xhr.open(${org.json.JSONObject.quote(method)}, ${org.json.JSONObject.quote(fullUrl)}, true);
                        xhr.withCredentials = true;
                        xhr.timeout = ${timeoutMs};
                        var headers = $headersJs;
                        Object.keys(headers).forEach(function(key) { xhr.setRequestHeader(key, headers[key]); });
                        function finish(code, body, error) {
                            if (!window.$slot) return;
                            window.$slot.result = JSON.stringify({code: code, body: body, error: error});
                            window.$slot.done = true;
                        }
                        xhr.onload = function() { finish(xhr.status, xhr.responseText, null); };
                        xhr.onerror = function() { finish(0, null, 'network_or_cors'); };
                        xhr.ontimeout = function() { finish(0, null, 'request_timeout'); };
                        xhr.onabort = function() { finish(0, null, 'request_aborted'); };
                        xhr.send(${if (jsonBody == null) "null" else org.json.JSONObject.quote(jsonBody)});
                    } catch (e) {
                        window.$slot.result = JSON.stringify({code: 0, error: String(e)});
                        window.$slot.done = true;
                    }
                })()
            """.trimIndent()

            val readJs = "(function() { var s = window.$slot; return (s && s.done) ? s.result : null; })()"
            val raw = try {
                wv.evaluateJavascript(js, null)
                // Allow the XHR timeout handler and the next poll to deliver the transport error.
                withTimeoutOrNull(timeoutMs + 3500L) {
                    var result: String? = null
                    while (result == null && webView === wv && pageGeneration == generation) {
                        result = evalOnMain(wv, readJs)
                        if (result == null) delay(250)
                    }
                    result
                }
            } finally {
                withContext(NonCancellable) {
                    if (webView === wv && pageGeneration == generation) {
                        wv.evaluateJavascript("delete window.$slot;", null)
                    }
                }
            }

            val decoded = runCatching { org.json.JSONTokener(raw.orEmpty()).nextValue() as? String }.getOrNull()
            val payload = decoded?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
            val code = payload?.optInt("code") ?: 0
            val body = payload?.opt("body") as? String
            val finishedAt = android.os.SystemClock.elapsedRealtime()
            DebugLog.log(
                TAG,
                "$method ${url.substringAfter("soundcloud.com")} -> $code, queue ${lockedAt - queuedAt} ms, page ${readyAt - lockedAt} ms, fetch ${finishedAt - readyAt} ms",
            )
            if (code == 0) {
                val error = payload?.opt("error") as? String ?: when {
                    webView !== wv -> "webview_replaced"
                    pageGeneration != generation -> "page_reloaded"
                    else -> "javascript_callback_timeout"
                }
                DebugLog.log(TAG, "$method ${url.substringAfter("soundcloud.com")} transport failure: $error")
            }
            BridgeResponse(code, body)
        }
    }

    // A usable document can be interactive before onPageFinished, and redirects can
    // leave the completion callback with a URL different from the committed document.
    private suspend fun awaitPage(wv: WebView): Int? {
        while (webView === wv) {
            val generation = pageGeneration
            if (evalOnMain(wv, PAGE_READY_JS) == "true" && webView === wv && pageGeneration == generation) {
                return generation
            }
            delay(250)
        }
        return null
    }

    private suspend fun appVersion(wv: WebView, generation: Int): String? {
        cachedAppVersion?.let { return it }
        val raw = evalOnMain(wv, "(function() { return window.__sc_version || (window.webpackJsonp && window.__sc_version) || null; })()")
        val value = raw?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "null" }
        Log.d(TAG, "app_version from page: $value")
        if (webView === wv && pageGeneration == generation) cachedAppVersion = value
        return value
    }

    private suspend fun evalOnMain(wv: WebView, js: String): String? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String?>()
        wv.evaluateJavascript(js) { result -> deferred.complete(result?.takeIf { it != "null" }) }
        withTimeoutOrNull(3000) { deferred.await() }
    }

    private companion object {
        const val BRIDGE_PAGE_URL = "https://soundcloud.com/discover"
        const val PAGE_READY_JS =
            "(location.origin === 'https://soundcloud.com' || location.origin === 'https://m.soundcloud.com') " +
                "&& (document.readyState === 'interactive' || document.readyState === 'complete')"
    }
}

data class BridgeResponse(val code: Int, val body: String?) {
    val isSuccess: Boolean get() = code in 200..299
}
