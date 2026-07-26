package com.savoo.scclient.ui.screens.account

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.savoo.scclient.R
import kotlinx.coroutines.delay

private const val SIGN_IN_URL = "https://soundcloud.com/signin"
private const val AUTO_LOGIN_TIMEOUT_MS = 18_000L
private const val FILL_POLL_INTERVAL_MS = 900L
private const val LOG_TAG = "ResonaLogin"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    onTokenReceived: (String) -> Unit,
    onCookiesReceived: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var attemptId by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    var browserStartUrl by remember { mutableStateOf(SIGN_IN_URL) }

    if (showBrowser) {
        OAuthWebViewScreen(
            startUrl = browserStartUrl,
            onTokenReceived = onTokenReceived,
            onCookiesReceived = onCookiesReceived,
            onCancel = { showBrowser = false },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        if (isSigningIn) {
            key(attemptId) {
                HiddenAutoLoginWebView(
                    email = email,
                    password = password,
                    onTokenReceived = { token ->
                        isSigningIn = false
                        onTokenReceived(token)
                    },
                    onCookiesReceived = onCookiesReceived,
                    onFailed = {
                        isSigningIn = false
                        errorMessage = null
                        browserStartUrl = SIGN_IN_URL
                        showBrowser = true
                    },
                    onCredentialsRejected = { message ->
                        isSigningIn = false
                        errorMessage = message
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.account_sign_in_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.account_sign_in_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.login_email_label)) },
                leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null) },
                singleLine = true,
                enabled = !isSigningIn,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password_label)) },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                enabled = !isSigningIn,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    errorMessage = null
                    attemptId++
                    isSigningIn = true
                },
                enabled = !isSigningIn && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSigningIn) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.login_signing_in))
                } else {
                    Text(stringResource(R.string.login_sign_in_button))
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.login_or_continue_with),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { browserStartUrl = SIGN_IN_URL; showBrowser = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.login_continue_google))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { browserStartUrl = SIGN_IN_URL; showBrowser = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.login_continue_facebook))
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { browserStartUrl = SIGN_IN_URL; showBrowser = true }) {
                Text(stringResource(R.string.login_trouble_use_browser))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HiddenAutoLoginWebView(
    email: String,
    password: String,
    onTokenReceived: (String) -> Unit,
    onCookiesReceived: (String) -> Unit,
    onFailed: () -> Unit,
    onCredentialsRejected: (String) -> Unit,
) {
    var succeeded by remember { mutableStateOf(false) }
    val capture = remember {
        WebViewTokenCapture(
            onTokenCaptured = { token ->
                succeeded = true
                onTokenReceived(token)
            },
            onCookiesCaptured = onCookiesReceived,
        )
    }

    LaunchedEffect(Unit) {
        delay(AUTO_LOGIN_TIMEOUT_MS)
        if (!succeeded) onFailed()
    }

    val cancelled = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) { onDispose { cancelled[0] = true } }

    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                alpha = 0f
                webViewClient = capture.newClient()
                loadUrl(SIGN_IN_URL)
                schedulePolledFill(
                    email = email,
                    password = password,
                    cancelled = cancelled,
                    isDone = { succeeded },
                    onCredentialsRejected = { message ->
                        succeeded = true
                        onCredentialsRejected(message)
                    },
                    onChallengeDetected = {
                        succeeded = true
                        onFailed()
                    },
                )
            }
        },
    )
}

private fun WebView.schedulePolledFill(
    email: String,
    password: String,
    cancelled: BooleanArray,
    isDone: () -> Boolean,
    onCredentialsRejected: (String) -> Unit,
    onChallengeDetected: () -> Unit,
) {
    val fillJs = buildFillScript(email, password)
    lateinit var tick: () -> Unit
    tick = {
        if (!cancelled[0] && !isDone()) {
            evaluateJavascript(fillJs) { rawResult ->
                val result = rawResult?.removeSurrounding("\"")?.replace("\\\"", "\"").orEmpty()
                com.savoo.scclient.debug.DebugLog.log(LOG_TAG, "fill attempt result: $result")
                when {
                    result.startsWith("error_detected:") ->
                        onCredentialsRejected(result.removePrefix("error_detected:"))
                    result.startsWith("challenge_detected:") ->
                        onChallengeDetected()
                }
            }
            postDelayed({ tick() }, FILL_POLL_INTERVAL_MS)
        }
    }
    postDelayed({ tick() }, FILL_POLL_INTERVAL_MS)
}

private fun jsString(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    return "\"$escaped\""
}

private fun buildFillScript(email: String, password: String): String = """
    (function() {
        try {
            function setNativeValue(el, value) {
                var proto = Object.getPrototypeOf(el);
                var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                if (desc && desc.set) { desc.set.call(el, value); } else { el.value = value; }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
            }
            function visible(el) {
                if (!el) return false;
                var r = el.getBoundingClientRect();
                return r.width > 0 && r.height > 0;
            }
            function findByText(selector, re) {
                var els = document.querySelectorAll(selector);
                for (var i = 0; i < els.length; i++) {
                    var t = (els[i].innerText || els[i].textContent || '').trim();
                    if (re.test(t) && visible(els[i])) return els[i];
                }
                return null;
            }
            function clickContinue(scope) {
                var root = scope || document;
                var btn = root.querySelector('button[type="submit"]') ||
                    findByText('button', /continue|next|log ?in|sign ?in/i) ||
                    findByText('[role="button"]', /continue|next|log ?in|sign ?in/i);
                if (btn) { btn.click(); return 'clicked_continue'; }
                if (scope && scope.tagName === 'FORM') {
                    if (scope.requestSubmit) scope.requestSubmit(); else scope.submit();
                    return 'submitted_form';
                }
                return null;
            }

            var challenge = document.querySelector(
                'iframe[src*="captcha" i], iframe[src*="hcaptcha" i], iframe[title*="captcha" i], ' +
                '.g-recaptcha, [data-hcaptcha-widget-id], [class*="challenge" i]'
            );
            if (challenge && visible(challenge)) return 'challenge_detected:captcha';

            var errorEls = document.querySelectorAll('[role="alert"], [class*="error" i]');
            for (var i = 0; i < errorEls.length; i++) {
                var errText = (errorEls[i].innerText || errorEls[i].textContent || '').trim();
                if (errText && errText.length < 200 && visible(errorEls[i])) {
                    return 'error_detected:' + errText;
                }
            }

            var emailInput = document.querySelector('input[type="email"]') ||
                document.querySelector('input[autocomplete="username"]') ||
                document.querySelector('input[name*="email" i]') ||
                document.querySelector('input[id*="email" i]');
            var passInput = document.querySelector('input[type="password"]');

            if (passInput && visible(passInput)) {
                if (emailInput && emailInput.value === '') setNativeValue(emailInput, ${jsString(email)});
                setNativeValue(passInput, ${jsString(password)});
                var form = passInput.closest('form') || (emailInput && emailInput.closest('form'));
                var r = clickContinue(form);
                return 'password_step:' + (r || 'no_submit_found');
            }

            if (emailInput && visible(emailInput)) {
                setNativeValue(emailInput, ${jsString(email)});
                var form2 = emailInput.closest('form');
                var r2 = clickContinue(form2);
                return 'email_step:' + (r2 || 'no_submit_found');
            }

            var emailEntry = findByText('button', /email/i) || findByText('a', /email/i) ||
                findByText('[role="button"]', /email/i);
            if (emailEntry) {
                emailEntry.click();
                return 'clicked_email_entry';
            }

            return 'no_match_yet';
        } catch (e) {
            return 'error:' + e.message;
        }
    })()
""".trimIndent()
