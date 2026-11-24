package com.savoo.scclient.ui.screens.account

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.savoo.scclient.R
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OAuthWebViewScreen(
    startUrl: String = "https://soundcloud.com",
    onTokenReceived: (String) -> Unit,
    onCookiesReceived: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var attempt by remember { mutableStateOf(0) }
    var pageError by remember { mutableStateOf<String?>(null) }
    val capture = remember(attempt) { WebViewTokenCapture() }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun closePopup() {
        popupWebView = null
        pageError = null
    }

    BackHandler {
        if (popupWebView != null) closePopup() else onCancel()
    }

    fun retry() {
        popupWebView = null
        pageError = null
        attempt++
    }

    Box(Modifier.fillMaxSize()) {
        key(attempt) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        configureSoundCloudLogin()
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(true)
                        webViewClient = capture.newClient { pageError = it }
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message,
                            ): Boolean {
                                if (popupWebView != null) return false
                                val popup = WebView(context).apply {
                                    configureSoundCloudLogin()
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    settings.setSupportMultipleWindows(true)
                                    webViewClient = capture.newClient { pageError = it }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCloseWindow(window: WebView) {
                                            popupWebView = null
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
                },
                onRelease = { it.destroy() },
            )
        }

        popupWebView?.let { popup ->
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { popup }, onRelease = { it.destroy() })

            IconButton(
                onClick = { closePopup() },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
        }

        if (popupWebView == null) {
            IconButton(onClick = { retry() }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.login_retry_page))
            }
        }

        pageError?.let { error ->
            Surface(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.login_page_load_failed, error), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { retry() }) { Text(stringResource(R.string.login_retry_page)) }
                }
            }
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
        }

        ExtendedFloatingActionButton(
            onClick = {
                val cookies = capture.cookiesFromManager() ?: capture.currentCookies
                // Discover can redirect to m.soundcloud.com. Check that page's OAuth
                // cookie without copying its other host-specific cookies into the app.
                val token = capture.mobilePageToken() ?: oauthTokenFromCookies(cookies) ?: capture.currentToken
                if (token != null) {
                    cookies?.takeIf { it.isNotEmpty() }?.let(onCookiesReceived)
                    onTokenReceived(token)
                } else {
                    val message = context.getString(
                        if (capture.returnedFromSignIn) R.string.login_browser_session_unavailable
                        else R.string.login_browser_not_signed_in,
                    )
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Text(stringResource(R.string.done), modifier = Modifier.padding(start = 8.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
        )
    }
}

internal fun oauthTokenFromCookies(cookies: String?): String? =
    cookies?.split(";")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("oauth_token=") }
        ?.substringAfter("=")
        ?.takeIf { it.length > 20 }
