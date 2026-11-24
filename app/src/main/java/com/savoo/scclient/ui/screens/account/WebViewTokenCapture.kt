package com.savoo.scclient.ui.screens.account

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.savoo.scclient.debug.DebugLog

private const val LOG_TAG = "ResonaLogin"

/** Hosts a SoundCloud login flow may need to leave soundcloud.com for (Google/Facebook/Apple
 * consent screens). Everything else stays blocked so this never turns into an open browser. */
private val ALLOWED_AUTH_HOSTS = listOf(
    "soundcloud.com",
    "captcha-delivery.com",
    "accounts.google.com",
    "appleid.apple.com",
    "www.facebook.com",
    "m.facebook.com",
)

private fun isAllowedAuthHost(host: String): Boolean =
    ALLOWED_AUTH_HOSTS.any { host == it || host.endsWith(".$it") }

internal fun isSoundCloudWebPage(url: Uri?): Boolean =
    url?.scheme == "https" && url.host?.let { it == "soundcloud.com" || it.endsWith(".soundcloud.com") } == true

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.configureSoundCloudLogin() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    val provider = WebView.getCurrentWebViewPackage()
    DebugLog.log(LOG_TAG, "WebView provider: ${provider?.packageName} ${provider?.versionName}")
}

/**
 * Shared SoundCloud web-session sniffing: pulls the OAuth token either off the `Authorization`
 * header of the page's own API calls, or out of the `window.__sc_hydration` JS blob once the
 * page has loaded, plus the session cookies. Used by both the visible (manual) and hidden
 * (auto-fill) login WebViews so the capture logic only lives in one place.
 */
class WebViewTokenCapture(
    private val onTokenCaptured: (String) -> Unit = {},
    private val onCookiesCaptured: (String) -> Unit = {},

) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mobilePageUrl: String? = null

    var currentToken: String? = null
        private set
    var currentCookies: String? = null
        private set
    var returnedFromSignIn = false
        private set

    fun cookiesFromManager(): String? = CookieManager.getInstance().getCookie("https://soundcloud.com/")

    fun mobilePageToken(): String? = mobilePageUrl?.let { url ->
        oauthTokenFromCookies(CookieManager.getInstance().getCookie(url))
    }

    fun newClient(onPageFailed: (String) -> Unit = {}): WebViewClient = object : WebViewClient() {
        private val loggedResourceErrors = mutableSetOf<String>()

        private fun reportFailure(request: WebResourceRequest, reason: String, showRetry: Boolean = true) {
            val host = request.url.host.orEmpty()
            val key = "$host $reason"
            if (request.isForMainFrame || (loggedResourceErrors.size < 12 && loggedResourceErrors.add(key))) {
                DebugLog.log(LOG_TAG, "${if (request.isForMainFrame) "page" else "resource"} load failed: $key")
            }
            if (request.isForMainFrame && showRetry) onPageFailed(reason)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            loggedResourceErrors.clear()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            reportFailure(request, "WebView ${error.errorCode}")
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            reportFailure(request, "HTTP ${errorResponse.statusCode}", showRetry = errorResponse.statusCode >= 500)
        }
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val host = request.url.host.orEmpty()
            val https = request.url.scheme == "https"
            if (!request.isForMainFrame) return !https
            val allowed = https && isAllowedAuthHost(host)
            DebugLog.log(LOG_TAG, "navigation to $host -> ${if (allowed) "allowed" else "blocked"}")
            return !allowed
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (request.url.scheme == "https" && request.url.host == "api-v2.soundcloud.com") {
                val auth = request.requestHeaders.entries
                    .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }?.value
                val token = auth?.takeIf { it.startsWith("OAuth ") }?.removePrefix("OAuth ")
                if (token != null && token.length > 20) {
                    mainHandler.post {
                        if (captureToken(token)) DebugLog.log(LOG_TAG, "token captured from SoundCloud API")
                    }
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            DebugLog.log(LOG_TAG, "page finished: ${url?.substringBefore('?')?.substringBefore('#')}")
            val uri = url?.let(Uri::parse)
            if (uri?.scheme == "https" && uri.host == "m.soundcloud.com") {
                mobilePageUrl = uri.buildUpon().clearQuery().fragment(null).build().toString()
            }
            if ((uri?.host == "soundcloud.com" || uri?.host == "m.soundcloud.com") &&
                uri.scheme == "https" && uri.path == "/signin/post-signin-redirect"
            ) {
                returnedFromSignIn = true
            }
            if (isSoundCloudWebPage(uri) && currentToken == null) {
                extractHydrationToken(view) { t ->
                    DebugLog.log(LOG_TAG, "hydration extraction result: ${if (t != null) "found token" else "none"}")
                    if (t != null) captureToken(t)
                }
            }
        }
    }

    private fun captureToken(token: String): Boolean {
        if (currentToken != null) return false
        currentToken = token
        currentCookies = cookiesFromManager()
        currentCookies?.let(onCookiesCaptured)
        onTokenCaptured(token)
        return true
    }
}

private fun extractHydrationToken(view: WebView, onResult: (String?) -> Unit) {
    val js = """
        (function() {
            try {
                var scripts = document.querySelectorAll('script');
                for (var i = 0; i < scripts.length; i++) {
                    var t = scripts[i].textContent || '';
                    if (t.indexOf('__sc_hydration') === -1) continue;
                    var re = /window\.__sc_hydration\s*=\s*(\[[\s\S]*?\])\s*;?\s*$/m;
                    var m = re.exec(t);
                    if (!m) continue;
                    var d = JSON.parse(m[1]);
                    for (var j = 0; j < d.length; j++) {
                        var s = d[j].data && d[j].data.session;
                        if (s && s.oauth_token) return 'token:' + s.oauth_token;
                        if (s && s.access_token) return 'token:' + s.access_token;
                    }
                }
            } catch(e) {}
            return 'none';
        })()
    """.trimIndent()

    view.evaluateJavascript(js) { result ->
        val raw = result?.removeSurrounding("\"")
            ?.replace("\\\"", "\"")
        when {
            raw?.startsWith("token:") == true -> onResult(raw.removePrefix("token:"))
            else -> onResult(null)
        }
    }
}
