package com.savoo.scclient.data.remote

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomServer @Inject constructor(
    private val prefs: SharedPreferences,
) {
    private val _url = MutableStateFlow(prefs.getString(KEY_URL, null)?.let(::parse))
    val url = _url.asStateFlow()

    val isActive: Boolean get() = _url.value != null

    fun set(value: String): Boolean {
        val parsed = parse(value) ?: return false
        prefs.edit().putString(KEY_URL, parsed.toString()).apply()
        _url.value = parsed
        return true
    }

    fun clear() {
        prefs.edit().remove(KEY_URL).apply()
        _url.value = null
    }

    fun rewrite(url: HttpUrl): HttpUrl {
        val base = _url.value ?: return url
        if (url.host != SC_API_HOST) return url
        val builder = base.newBuilder().encodedQuery(url.encodedQuery)
        val baseSegments = base.encodedPathSegments.filter { it.isNotEmpty() }
        builder.encodedPath("/")
        (baseSegments + url.encodedPathSegments).forEach { builder.addEncodedPathSegment(it) }
        return builder.build()
    }

    companion object {
        private const val KEY_URL = "custom_server_url"
        const val SC_API_HOST = "api-v2.soundcloud.com"

        fun isSoundCloudHost(host: String): Boolean =
            host == "soundcloud.com" || host.endsWith(".soundcloud.com")

        private fun parse(value: String): HttpUrl? {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            return withScheme.toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }
        }
    }
}

class CustomServerInterceptor @Inject constructor(
    private val customServer: CustomServer,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val target = customServer.rewrite(request.url)
        if (target == request.url) return chain.proceed(request)
        return chain.proceed(request.newBuilder().url(target).build())
    }
}
