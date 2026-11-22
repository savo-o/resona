package com.savoo.scclient.data.remote

import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.auth.SessionRefresher
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.CancellationException
import kotlin.uuid.Uuid

internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

class AuthInterceptor(
    private val clientIdProvider: ClientIdProvider,
    private val tokenStore: TokenStore,
    private val challenge: DataDomeChallenge,
    private val sessionRefresher: SessionRefresher,
) {

    fun install(client: HttpClient) {
        client.plugin(HttpSend).intercept { request -> intercept(request) }
    }

    private suspend fun Sender.proceed(request: HttpRequestBuilder): HttpClientCall = try {
        execute(request).also { ConnectivityEventBus.notifyReachable() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ConnectivityEventBus.notifyUnreachable()
        throw e
    }

    private suspend fun Sender.intercept(request: HttpRequestBuilder): HttpClientCall {
        if (!isSoundCloudHost(request.url.host)) return proceed(request)

        var clientId = clientIdProvider.cachedOrFallback().ifBlank { clientIdProvider.refresh() }
        var token = tokenStore.accessToken
        request.url.parameters.remove("client_id")
        request.url.parameters.append("client_id", clientId)
        applyCommonHeaders(request, clientId, token)

        var call = proceed(request).save()
        val isWrite = request.method.value != "GET"
        if (isWrite && call.response.status.value in 200..299) {
            challenge.recordWrite(call.response.status.value, null, afterSolve = false)
        }

        val challengeUrl = DataDomeChallenge.challengeUrl(
            call.response.status.value,
            runCatching { call.response.bodyAsText().take(64 * 1024) }.getOrNull(),
        )
        if (challengeUrl != null) {
            if (!challenge.solve(challengeUrl)) return call
            applyCommonHeaders(request, clientId)
            val retried = proceed(request)
            if (!isWrite) return retried
            val saved = retried.save()
            challenge.recordWrite(
                saved.response.status.value,
                runCatching { saved.response.bodyAsText().take(64 * 1024) }.getOrNull(),
                afterSolve = true,
            )
            return saved
        }

        if (call.response.status.value == 401 || call.response.status.value == 403) {
            val freshClientId = clientIdProvider.refresh()
            if (freshClientId != clientId) {
                clientId = freshClientId
                request.url.parameters.remove("client_id")
                request.url.parameters.append("client_id", clientId)
                applyCommonHeaders(request, clientId, token)
                call = proceed(request).save()
            }
        }
        val rejectedToken = token
        if (call.response.status.value == 401 && rejectedToken != null) {
            val freshToken = sessionRefresher.refresh(rejectedToken, clientId)
            if (freshToken != null && freshToken != token) {
                token = freshToken
                applyCommonHeaders(request, clientId, token)
                call = proceed(request).save()
            }
            val path = request.url.build().encodedPath
            val publicRead = path.startsWith("/search/") || path.startsWith("/tracks/") ||
                path.startsWith("/media/") || path == "/resolve" || path == "/charts"
            if (call.response.status.value == 401 && request.method.value == "GET" && publicRead) {
                applyCommonHeaders(request, clientId, null, includeCookies = false)
                call = proceed(request)
            }
        }
        return call
    }

    private fun applyCommonHeaders(
        builder: HttpRequestBuilder,
        clientId: String,
        accessToken: String? = tokenStore.accessToken,
        includeCookies: Boolean = true,
    ) {
        builder.headers.remove("Authorization")
        builder.headers.remove("Cookie")
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Accept" to "application/json, text/javascript, */*; q=0.1",
            "Origin" to "https://soundcloud.com",
            "Referer" to "https://soundcloud.com/",
            "X-SoundCloud-Client" to clientId,
            "X-Client-UUID" to Uuid.random().toString(),
        ) + listOfNotNull(
            accessToken?.let { "Authorization" to "OAuth $it" },
            tokenStore.webCookies?.takeIf { includeCookies }?.let { "Cookie" to it },
        )
        headers.forEach { (name, value) ->
            builder.headers.remove(name)
            builder.headers.append(name, value)
        }
    }
}

internal fun isSoundCloudHost(host: String): Boolean =
    host == "soundcloud.com" || host.endsWith(".soundcloud.com")
