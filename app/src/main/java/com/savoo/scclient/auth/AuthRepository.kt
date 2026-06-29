package com.savoo.scclient.auth

import com.savoo.scclient.data.model.TokenResponse
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.di.PlainHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @PlainHttpClient private val client: OkHttpClient,
    private val clientIdProvider: ClientIdProvider,
    private val tokenStore: TokenStore,
    private val moshi: Moshi,
) {
    suspend fun exchangeCodeForToken(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientIdProvider.cachedOrFallback())
                .add("redirect_uri", OAuthConfig.REDIRECT_URI)
                .add("code", code)
                .build()

            val request = Request.Builder()
                .url(OAuthConfig.TOKEN_URL)
                .post(body)
                .build()

            val responseBody = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("Token exchange failed: ${resp.code}")
                resp.body?.string().orEmpty()
            }

            val adapter = moshi.adapter(TokenResponse::class.java)
            val token = adapter.fromJson(responseBody) ?: error("Empty token response")
            tokenStore.save(token)
        }
    }

    fun logout() = tokenStore.clear()
}
