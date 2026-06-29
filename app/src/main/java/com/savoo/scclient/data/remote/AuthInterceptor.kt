package com.savoo.scclient.data.remote

import com.savoo.scclient.auth.TokenStore
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

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
                    tokenStore.accessToken?.let { addHeader("Authorization", "OAuth $it") }
                    tokenStore.webCookies?.let { addHeader("Cookie", it) }
                }
                .build()
            response = chain.proceed(retried)
        }

        return response
    }
}
