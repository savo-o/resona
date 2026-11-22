package com.savoo.scclient.auth

import android.content.Context
import android.content.SharedPreferences
import com.savoo.scclient.data.local.createSecurePrefs
import com.savoo.scclient.data.model.TokenResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStore internal constructor(private val prefs: SharedPreferences) {
    @Inject constructor(@ApplicationContext context: Context) :
        this(createSecurePrefs(context, "sc_auth_secure_prefs"))

    private val _isLoggedIn = MutableStateFlow(accessToken != null)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        private set(value) { prefs.edit().putString(KEY_ACCESS, value).apply() }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
            ?: webCookies?.split(';')?.map { it.trim() }
                ?.firstOrNull { it.startsWith("oauth_refresh_token=") }
                ?.substringAfter('=')?.takeIf { it.isNotBlank() }
        private set(value) { prefs.edit().putString(KEY_REFRESH, value).apply() }

    fun save(token: TokenResponse) {
        accessToken = token.accessToken
        refreshToken = token.refreshToken
        if (webCookies.orEmpty().split(';').any { it.trim().startsWith("oauth_token=") }) {
            replaceCookie("oauth_token=${token.accessToken}")
        }
        token.refreshToken?.let { refreshed ->
            if (webCookies.orEmpty().split(';').any { it.trim().startsWith("oauth_refresh_token=") }) {
                replaceCookie("oauth_refresh_token=$refreshed")
            }
        }
        _isLoggedIn.value = true
    }

    fun saveWebToken(accessTokenValue: String) {
        accessToken = accessTokenValue
        refreshToken = null
        if (webCookies.orEmpty().split(';').any { it.trim().startsWith("oauth_token=") }) {
            replaceCookie("oauth_token=$accessTokenValue")
        }
        _isLoggedIn.value = true
    }

    fun saveCookies(cookies: String) {
        webCookies = cookies
    }

    fun replaceCookie(pair: String) {
        val name = pair.substringBefore("=")
        val kept = webCookies.orEmpty().split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.substringBefore("=") != name }
        webCookies = (kept + pair).joinToString("; ")
    }

    var webCookies: String?
        get() = prefs.getString(KEY_COOKIES, null)
        private set(value) { prefs.edit().putString(KEY_COOKIES, value).apply() }

    fun clear() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
    }

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_COOKIES = "web_cookies"
    }
}
