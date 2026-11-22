package com.savoo.scclient.data.remote

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ClientIdProviderTest {
    @Test fun prefersCurrentHydratedClientIdOverBundledJavaScript() = runBlocking {
        val id = "b".repeat(32)
        var count = 0
        val provider = provider {
            count++
            """<script>window.__sc_hydration = [{"hydratable":"apiClient","data":{"id":"$id","isExpiring":true}}];</script><script src="https://a-v2.sndcdn.com/assets/app.js"></script>"""
        }
        assertEquals(id, provider.refresh())
        assertEquals(id, provider.cachedOrFallback())
        assertEquals(1, count)
    }

    @Test fun continuesPastFailedScriptAndAcceptsQuotedPropertyWithWhitespace() = runBlocking {
        val id = "c".repeat(32)
        val provider = provider { path ->
            when (path) {
                "/" -> "<script src='https://a-v2.sndcdn.com/assets/good.js'></script><script src='https://a-v2.sndcdn.com/assets/broken.js'></script>"
                "/assets/good.js" -> "{\"client_id\" : \"$id\"}"
                else -> throw IOException("Network failure")
            }
        }
        assertEquals(id, provider.refresh())
    }

    @Test fun preservesCachedIdWhenRefreshFails() = runBlocking {
        val provider = provider { throw IOException("Offline") }
        val id = "d".repeat(32)
        provider.setManualOverride(id)
        assertEquals(id, provider.refresh(force = true))
        assertEquals(id, provider.cachedOrFallback())
    }

    @Test fun clearRemovesCachedIdAndAllowsImmediateRefresh() = runBlocking {
        var count = 0
        val id = "e".repeat(32)
        val provider = provider {
            count++
            """<script>window.__sc_hydration = [{"hydratable":"apiClient","data":{"id":"$id"}}];</script>"""
        }
        assertEquals(id, provider.refresh())
        provider.clear()
        assertEquals("", provider.cachedOrFallback())
        assertEquals(id, provider.refresh())
        assertEquals(2, count)
    }

    @Test fun explicitRefreshReportsFailureAndKeepsSavedId() = runBlocking {
        val provider = provider { throw IOException("Offline") }
        val id = "f".repeat(32)
        provider.setManualOverride(id)
        assertTrue(runCatching { provider.refresh(force = true, failOnError = true) }.isFailure)
        assertEquals(id, provider.cachedOrFallback())
    }

    private fun provider(body: (String) -> String): ClientIdProvider {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("client_id_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body(chain.request().url.encodedPath).toResponseBody()).build()
        }.build()
        return ClientIdProvider(prefs, client)
    }
}
