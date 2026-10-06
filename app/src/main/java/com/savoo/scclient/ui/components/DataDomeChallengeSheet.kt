package com.savoo.scclient.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.savoo.scclient.R
import com.savoo.scclient.data.remote.BROWSER_USER_AGENT
import com.savoo.scclient.data.remote.PendingChallenge
import com.savoo.scclient.ui.haptics.rememberHaptics
import kotlinx.coroutines.delay
import org.json.JSONObject

private fun hostPage(challengeUrl: String): String = """
    <!doctype html>
    <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
    <style>html,body{margin:0;height:100%;background:#fff}iframe{border:0;width:100%;height:100%}</style>
    </head><body>
    <iframe src=${JSONObject.quote(challengeUrl)} allow="cross-origin-isolated"></iframe>
    <script>
    window.__ddCookie = null;
    window.addEventListener('message', function(e) {
        if (!/captcha-delivery\.com$/.test(new URL(e.origin).hostname)) return;
        var data = e.data;
        try { if (typeof data === 'string') data = JSON.parse(data); } catch (_) { return; }
        if (data && typeof data.cookie === 'string' && data.cookie.indexOf('datadome=') === 0) {
            window.__ddCookie = data.cookie;
        }
    });
    </script>
    </body></html>
""".trimIndent()

private fun isChallengeHost(host: String): Boolean =
    host == "soundcloud.com" || host.endsWith(".soundcloud.com") || host.endsWith("captcha-delivery.com")

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DataDomeChallengeSheet(
    challenge: PendingChallenge,
    onPassed: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    var webView by remember { mutableStateOf<WebView?>(null) }
    val tint = if (challenge.hardBlock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    LaunchedEffect(webView, challenge.url) {
        val wv = webView ?: return@LaunchedEffect
        while (true) {
            delay(500)
            val cookie = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                wv.evaluateJavascript("window.__ddCookie") { raw ->
                    val value = runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull()
                    cont.resumeWith(Result.success(value))
                }
            }
            if (cookie != null) {
                haptics.success()
                onPassed(cookie)
                return@LaunchedEffect
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (challenge.hardBlock) Icons.Filled.Block else Icons.Filled.Shield,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (challenge.hardBlock) R.string.datadome_blocked_title else R.string.datadome_title
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (challenge.hardBlock) R.string.datadome_blocked_subtitle else R.string.datadome_subtitle
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .clip(RoundedCornerShape(24.dp)),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = BROWSER_USER_AGENT
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = !isChallengeHost(request.url.host.orEmpty())
                        }
                        loadDataWithBaseURL(
                            "https://soundcloud.com/",
                            hostPage(challenge.url),
                            "text/html",
                            "utf-8",
                            null,
                        )
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.datadome_close))
            }
        }
    }
}
