package com.savoo.scclient.ui.screens.account

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OAuthWebViewScreen(
    onTokenReceived: (String) -> Unit,
    onCookiesReceived: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var token by remember { mutableStateOf<String?>(null) }
    var tokenCookies by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val host = request.url.host.orEmpty()
                            if (!host.endsWith("soundcloud.com")) {
                                return true
                            }
                            return false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val url = request.url.toString()
                            val auth = request.requestHeaders?.get("Authorization")
                            if (auth != null && auth.startsWith("OAuth ") && token == null
                                && url.contains("api-v2.soundcloud.com")) {
                                val t = auth.removePrefix("OAuth ")
                                if (t.length > 20) {
                                    token = t
                                    tokenCookies = CookieManager.getInstance().getCookie("soundcloud.com")
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            tryExtractToken(view) { t ->
                                if (t != null && token == null) {
                                    token = t
                                }
                            }
                        }
                    }
                    loadUrl("https://soundcloud.com")
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
                webView
            }
        )

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }

        ExtendedFloatingActionButton(
            onClick = {
                val captured = token
                if (captured != null) {
                    onTokenReceived(captured)
                    tokenCookies?.let { onCookiesReceived(it) }
                } else {
                    val cookies = CookieManager.getInstance()
                        .getCookie("soundcloud.com") ?: ""
                    if (cookies.isNotEmpty()) {
                        onCookiesReceived(cookies)
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Text(stringResource(R.string.done), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun tryExtractToken(view: WebView, onResult: (String?) -> Unit) {
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
