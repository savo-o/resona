package com.savoo.scclient.ui.screens.account

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

private const val LOG_TAG = "ResonaLogin"

/** Hosts a SoundCloud login flow may need to leave soundcloud.com for (Google/Facebook/Apple
 * consent screens). Everything else stays blocked so this never turns into an open browser. */
private val ALLOWED_AUTH_HOSTS = listOf(
    "soundcloud.com",
    "accounts.google.com",
    "appleid.apple.com",
    "www.facebook.com",
    "m.facebook.com",
)

private fun isAllowedAuthHost(host: String): Boolean =
    ALLOWED_AUTH_HOSTS.any { host == it || host.endsWith(".$it") }

/**
 * Shared SoundCloud web-session sniffing: pulls the OAuth token either off the `Authorization`
 * header of the page's own API calls, or out of the `window.__sc_hydration` JS blob once the
 * page has loaded, plus the session cookies. Used by both the visible (manual) and hidden
 * (auto-fill) login WebViews so the capture logic only lives in one place.
 */
class WebViewTokenCapture(
    private val onTokenCaptured: (String) -> Unit = {},
    private val onCookiesCaptured: (String) -> Unit = {},
    private val onPageLoaded: (() -> Unit)? = null,
) {
    var currentToken: String? = null
        private set
    var currentCookies: String? = null
        private set

    fun cookiesFromManager(): String? = CookieManager.getInstance().getCookie("soundcloud.com")

    fun newClient(): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val host = request.url.host.orEmpty()
            val allowed = isAllowedAuthHost(host)
            if (!request.isForMainFrame) return !allowed
            Log.d(LOG_TAG, "navigation to $host -> ${if (allowed) "allowed" else "blocked"}")
            return !allowed
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val url = request.url.toString()
            val auth = request.requestHeaders?.get("Authorization")
            if (auth != null && auth.startsWith("OAuth ") && currentToken == null &&
                url.contains("api-v2.soundcloud.com")
            ) {
                val t = auth.removePrefix("OAuth ")
                if (t.length > 20) {
                    Log.d(LOG_TAG, "token captured from Authorization header on $url")
                    currentToken = t
                    currentCookies = cookiesFromManager()
                    onTokenCaptured(t)
                    currentCookies?.let(onCookiesCaptured)
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            Log.d(LOG_TAG, "page finished: $url")
            onPageLoaded?.invoke()
            if (currentToken == null) {
                extractHydrationToken(view) { t ->
                    Log.d(LOG_TAG, "hydration extraction result: ${if (t != null) "found token" else "none"}")
                    if (t != null && currentToken == null) {
                        currentToken = t
                        onTokenCaptured(t)
                    }
                }
            }
        }
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
