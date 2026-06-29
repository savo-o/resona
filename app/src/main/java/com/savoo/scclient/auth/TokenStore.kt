package com.savoo.scclient.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.savoo.scclient.data.model.TokenResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "sc_auth_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isLoggedIn = MutableStateFlow(accessToken != null)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        private set(value) { prefs.edit().putString(KEY_ACCESS, value).apply() }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        private set(value) { prefs.edit().putString(KEY_REFRESH, value).apply() }

    fun save(token: TokenResponse) {
        accessToken = token.accessToken
        refreshToken = token.refreshToken
        _isLoggedIn.value = true
    }

    fun saveWebToken(accessTokenValue: String) {
        accessToken = accessTokenValue
        _isLoggedIn.value = true
    }

    fun saveCookies(cookies: String) {
        webCookies = cookies
        if (accessToken == null && cookies.isNotEmpty()) {
            _isLoggedIn.value = true
        }
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
