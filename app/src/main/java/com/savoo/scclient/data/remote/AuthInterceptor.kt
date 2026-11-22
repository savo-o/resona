package com.savoo.scclient.data.remote

import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.auth.SessionRefresher
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject

internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

class AuthInterceptor @Inject constructor(
    private val clientIdProvider: ClientIdProvider,
    private val tokenStore: TokenStore,
    private val challenge: DataDomeChallenge,
    private val sessionRefresher: SessionRefresher,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!isSoundCloudHost(original.url.host)) return chain.proceed(original)

        var clientId = clientIdProvider.cachedOrFallback().ifBlank {
            runBlocking { clientIdProvider.refresh() }
        }
        var token = tokenStore.accessToken

        fun request(id: String, accessToken: String?, includeCookies: Boolean = true): okhttp3.Request =
            original.newBuilder()
                .url(original.url.newBuilder().setQueryParameter("client_id", id).build())
                .removeHeader("Authorization")
                .removeHeader("Cookie")
                .apply {
                    applyCommonHeaders(this, id)
                    accessToken?.let { header("Authorization", "OAuth $it") }
                    if (includeCookies) tokenStore.webCookies?.let { header("Cookie", it) }
                }
                .build()

        var response = chain.proceed(request(clientId, token))
        val isWrite = original.method != "GET"
        if (isWrite && response.isSuccessful) challenge.recordWrite(response.code, null, afterSolve = false)

        val challengeUrl = DataDomeChallenge.challengeUrl(
            response.code,
            runCatching { response.peekBody(64 * 1024).string() }.getOrNull(),
        )
        if (challengeUrl != null) {
            if (!runBlocking { challenge.solve(challengeUrl) }) return response
            response.close()
            val retriedResponse = chain.proceed(request(clientId, tokenStore.accessToken))
            if (isWrite) {
                challenge.recordWrite(
                    retriedResponse.code,
                    runCatching { retriedResponse.peekBody(64 * 1024).string() }.getOrNull(),
                    afterSolve = true,
                )
            }
            return retriedResponse
        }

        if (response.code == 401 || response.code == 403) {
            val freshClientId = runBlocking { clientIdProvider.refresh() }
            if (freshClientId != clientId) {
                response.close()
                clientId = freshClientId
                response = chain.proceed(request(clientId, token))
            }
        }
        if (response.code == 401 && token != null) {
            val freshToken = runBlocking { sessionRefresher.refresh(token, clientId) }
            if (freshToken != null && freshToken != token) {
                response.close()
                token = freshToken
                response = chain.proceed(request(clientId, token))
            }
            if (response.code == 401 && original.method == "GET" && isPublicRead(original.url)) {
                response.close()
                response = chain.proceed(request(clientId, null, includeCookies = false))
            }
        }

        return response
    }

    private fun isPublicRead(url: HttpUrl): Boolean =
        url.encodedPath.startsWith("/search/") || url.encodedPath.startsWith("/tracks/") ||
            url.encodedPath.startsWith("/media/") || url.encodedPath == "/resolve" ||
            url.encodedPath == "/charts"

    private fun applyCommonHeaders(builder: okhttp3.Request.Builder, clientId: String) {
        builder.header("User-Agent", BROWSER_USER_AGENT)
        builder.header("Accept", "application/json, text/javascript, */*; q=0.1")
        builder.header("Origin", "https://soundcloud.com")
        builder.header("Referer", "https://soundcloud.com/")
        builder.header("X-SoundCloud-Client", clientId)
        builder.header("X-Client-UUID", UUID.randomUUID().toString())
    }
}

internal fun isSoundCloudHost(host: String): Boolean =
    host == "soundcloud.com" || host.endsWith(".soundcloud.com")
