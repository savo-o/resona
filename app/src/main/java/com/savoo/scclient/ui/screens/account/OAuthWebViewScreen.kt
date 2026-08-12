package com.savoo.scclient.ui.screens.account

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.viewinterop.AndroidView
import com.savoo.scclient.R

private const val OAUTH_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OAuthWebViewScreen(
    startUrl: String = "https://soundcloud.com",
    onTokenReceived: (String) -> Unit,
    onCookiesReceived: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val capture = remember { WebViewTokenCapture() }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)
                    settings.userAgentString = OAUTH_USER_AGENT
                    webViewClient = capture.newClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message,
                        ): Boolean {
                            val popup = WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.setSupportMultipleWindows(true)
                                settings.userAgentString = OAUTH_USER_AGENT
                                webViewClient = capture.newClient()
                                webChromeClient = object : WebChromeClient() {
                                    override fun onCloseWindow(window: WebView) {
                                        popupWebView = null
                                        window.destroy()
                                    }
                                }
                            }
                            popupWebView = popup
                            val transport = resultMsg.obj as WebView.WebViewTransport
                            transport.webView = popup
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    loadUrl(startUrl)
                }
            }
        )

        popupWebView?.let { popup ->
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { popup })

            IconButton(
                onClick = {
                    popup.destroy()
                    popupWebView = null
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }

        ExtendedFloatingActionButton(
            onClick = {
                val captured = capture.currentToken
                if (captured != null) {
                    onTokenReceived(captured)
                    capture.currentCookies?.let(onCookiesReceived)
                } else {
                    val cookies = capture.cookiesFromManager().orEmpty()
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
