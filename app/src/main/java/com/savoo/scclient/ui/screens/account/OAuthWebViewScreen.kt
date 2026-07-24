package com.savoo.scclient.ui.screens.account

import android.annotation.SuppressLint
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.savoo.scclient.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OAuthWebViewScreen(
    startUrl: String = "https://soundcloud.com",
    onTokenReceived: (String) -> Unit,
    onCookiesReceived: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val capture = remember { WebViewTokenCapture() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    webViewClient = capture.newClient()
                    loadUrl(startUrl)
                }
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
