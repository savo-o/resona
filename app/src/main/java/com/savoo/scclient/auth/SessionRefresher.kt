package com.savoo.scclient.auth

import com.savoo.scclient.data.model.TokenResponse
import com.savoo.scclient.data.remote.BROWSER_USER_AGENT
import com.savoo.scclient.data.remote.soundCloudHydration
import com.savoo.scclient.di.PlainHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRefresher @Inject constructor(
    private val tokenStore: TokenStore,
    @PlainHttpClient private val client: OkHttpClient,
) {
    private val mutex = Mutex()
    private var lastFailedToken: String? = null
    private var lastFailedAt = 0L

    suspend fun refresh(rejectedToken: String, clientId: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = tokenStore.accessToken ?: return@withLock null
            if (current != rejectedToken) return@withLock current
            val now = System.currentTimeMillis()
            if (lastFailedToken == current && now - lastFailedAt < 30000L) return@withLock null
            val refreshed = runCatching {
                runCatching { refreshOAuth(clientId, rejectedToken) }.getOrNull()
                    ?: refreshWebSession(clientId, rejectedToken)
            }.getOrNull()
            if (refreshed == null) {
                lastFailedToken = current
                lastFailedAt = now
            } else {
                lastFailedToken = null
            }
            refreshed
        }
    }

    private fun refreshOAuth(clientId: String, rejectedToken: String): String? {
        val refreshToken = tokenStore.refreshToken ?: return null
        val request = Request.Builder().url(OAuthConfig.TOKEN_URL)
            .header("User-Agent", BROWSER_USER_AGENT)
            .post(FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
                .build())
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string() ?: return@use null)
            val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return@use null
            if (tokenStore.accessToken != rejectedToken) return@use tokenStore.accessToken
            tokenStore.save(TokenResponse(access,
                json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken,
                json.optLong("expires_in"), json.optString("scope")))
            access
        }
    }

    private fun refreshWebSession(clientId: String, rejectedToken: String): String? {
        val cookies = tokenStore.webCookies?.takeIf { it.isNotBlank() } ?: return null
        val request = Request.Builder().url("https://soundcloud.com/")
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Cookie", cookies)
            .header("Cache-Control", "no-cache")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val html = response.body?.string() ?: return@use null
            val token = sessionToken(html) ?: return@use null
            if (token == tokenStore.accessToken) return@use null
            val verification = Request.Builder()
                .url("https://api-v2.soundcloud.com/me?client_id=$clientId")
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Authorization", "OAuth $token")
                .build()
            val valid = client.newCall(verification).execute().use {
                it.isSuccessful && JSONObject(it.body?.string().orEmpty()).optLong("id") > 0
            }
            if (!valid) return@use null
            if (tokenStore.accessToken != rejectedToken) return@use tokenStore.accessToken
            response.headers.values("Set-Cookie").forEach { tokenStore.replaceCookie(it.substringBefore(';')) }
            tokenStore.saveWebToken(token)
            token
        }
    }

    private fun sessionToken(html: String): String? {
        for (entry in soundCloudHydration(html)) {
            val session = entry.optJSONObject("data")?.optJSONObject("session") ?: continue
            for (key in listOf("oauth_token", "access_token")) {
                session.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { return it }
            }
        }
        return null
    }
}
