package com.savoo.scclient.data.remote

import com.savoo.scclient.auth.TokenStore
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

// Same UA already used for the login WebView (WebViewTokenCapture/LoginScreen) - api-v2 calls
// with no browser-like User-Agent (OkHttp's default literally says "okhttp/...") are an easy
// signal for SoundCloud's edge protection to flag, independent of any auth/client_id correctness.
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

class AuthInterceptor @Inject constructor(
    private val clientIdProvider: ClientIdProvider,
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val urlWithClientId: HttpUrl = original.url.newBuilder()
            .apply {
                if (original.url.queryParameter("client_id") == null) {
                    addQueryParameter("client_id", clientIdProvider.cachedOrFallback())
                }
            }
            .build()

        val requestBuilder = original.newBuilder().url(urlWithClientId)
        applyCommonHeaders(requestBuilder)
        tokenStore.accessToken?.let { token ->
            requestBuilder.addHeader("Authorization", "OAuth $token")
        }
        tokenStore.webCookies?.let { cookies ->
            requestBuilder.addHeader("Cookie", cookies)
        }

        var response = chain.proceed(requestBuilder.build())

        if (response.code == 401 || response.code == 403) {
            response.close()
            val retried = original.newBuilder()
                .url(
                    original.url.newBuilder()
                        .removeAllQueryParameters("client_id")
                        .addQueryParameter("client_id", clientIdProvider.cachedOrFallback())
                        .build()
                )
                .apply {
                    applyCommonHeaders(this)
                    tokenStore.accessToken?.let { addHeader("Authorization", "OAuth $it") }
                    tokenStore.webCookies?.let { addHeader("Cookie", it) }
                }
                .build()
            response = chain.proceed(retried)
        }

        return response
    }

    private fun applyCommonHeaders(builder: okhttp3.Request.Builder) {
        builder.header("User-Agent", BROWSER_USER_AGENT)
        builder.header("Accept", "application/json, text/javascript, */*; q=0.1")
    }
}
