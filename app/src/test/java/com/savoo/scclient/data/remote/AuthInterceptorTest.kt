package com.savoo.scclient.data.remote

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.savoo.scclient.auth.SessionRefresher
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.model.TokenResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AuthInterceptorTest {
    private lateinit var tokens: TokenStore
    private lateinit var ids: ClientIdProvider
    private val clientId = "a".repeat(32)
    private val requests = mutableListOf<Request>()
    private val refreshRequests = mutableListOf<Request>()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_auth", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        tokens = TokenStore(prefs)
        val idPrefs = context.getSharedPreferences("test_ids", Context.MODE_PRIVATE)
        idPrefs.edit().clear().commit()
        val plain = fakeClient { request ->
            if (request.url.host == "soundcloud.com") response(request, 200, "<script src='https://a-v2.sndcdn.com/assets/app.js'></script>")
            else response(request, 200, "client_id : '$clientId'")
        }
        ids = ClientIdProvider(idPrefs, plain)
        ids.setManualOverride(clientId)
    }

    @Test fun replacesStaleClientIdInPaginatedUrl() {
        val client = authenticatedClient(fakeClient { response(it, 500) }) { response(it, 200) }
        client.newCall(Request.Builder().url("https://api-v2.soundcloud.com/search/tracks?client_id=old&client_id=older").build()).execute().close()
        assertEquals(listOf(clientId), requests.single().url.queryParameterValues("client_id"))
    }

    @Test fun retriesPublicTrackWithoutExpiredSession() {
        tokens.saveWebToken("expired")
        tokens.saveCookies("oauth_token=expired; session=old")
        val client = authenticatedClient(fakeClient { response(it, 503) }) {
            response(it, if (it.header("Authorization") == null) 200 else 401)
        }
        client.newCall(Request.Builder().url("https://api-v2.soundcloud.com/tracks/123").build()).execute().use {
            assertEquals(200, it.code)
        }
        assertEquals(2, requests.size)
        assertNull(requests.last().header("Authorization"))
        assertNull(requests.last().header("Cookie"))
        assertEquals("expired", tokens.accessToken)
    }

    @Test fun neverFallsBackToAnonymousForAccountWritesOrMe() {
        tokens.saveWebToken("expired")
        val client = authenticatedClient(fakeClient { response(it, 503) }) { response(it, 401) }
        for (url in listOf("https://api-v2.soundcloud.com/me", "https://api-v2.soundcloud.com/users/1/track_likes/2")) {
            val request = Request.Builder().url(url).apply { if (url.endsWith("/2")) delete() }.build()
            client.newCall(request).execute().use { assertEquals(401, it.code) }
        }
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.header("Authorization") == "OAuth expired" })
    }

    @Test fun refreshesOAuthTokenAndRetriesAuthenticatedRequest() {
        tokens.save(TokenResponse("expired", "refresh", 3600, null))
        val refresh = fakeClient {
            refreshRequests.add(it)
            response(it, 200, """{"access_token":"fresh","refresh_token":"rotated","expires_in":3600}""")
        }
        val client = authenticatedClient(refresh) { response(it, if (it.header("Authorization") == "OAuth fresh") 200 else 401) }
        client.newCall(Request.Builder().url("https://api-v2.soundcloud.com/me").build()).execute().use { assertEquals(200, it.code) }
        assertEquals("fresh", tokens.accessToken)
        assertEquals("rotated", tokens.refreshToken)
        assertEquals(1, refreshRequests.size)
        assertEquals(2, requests.size)
    }

    @Test fun refreshesBrowserSessionUsingRefreshTokenCookie() {
        tokens.saveWebToken("expired")
        tokens.saveCookies("oauth_token=expired; oauth_refresh_token=browser-refresh")
        val refresh = fakeClient {
            refreshRequests.add(it)
            val body = okio.Buffer()
            it.body!!.writeTo(body)
            assertTrue(body.readUtf8().contains("refresh_token=browser-refresh"))
            response(it, 200, """{"access_token":"fresh-web","refresh_token":"rotated-web","expires_in":3600}""")
        }
        val client = authenticatedClient(refresh) { response(it, if (it.header("Authorization") == "OAuth fresh-web") 200 else 401) }
        client.newCall(Request.Builder().url("https://api-v2.soundcloud.com/me").build()).execute().use { assertEquals(200, it.code) }
        assertEquals("fresh-web", tokens.accessToken)
        assertEquals("rotated-web", tokens.refreshToken)
        assertTrue(tokens.webCookies!!.contains("oauth_token=fresh-web"))
        assertTrue(tokens.webCookies!!.contains("oauth_refresh_token=rotated-web"))
        assertEquals(1, refreshRequests.size)
    }

    @Test fun recoversWebTokenFromAuthenticatedHomepage() {
        tokens.saveWebToken("expired")
        tokens.saveCookies("session=valid; oauth_token=expired")
        val refresh = fakeClient {
            refreshRequests.add(it)
            if (it.url.host == "soundcloud.com") {
                response(it, 200, """<script>window.__sc_hydration = [{"data":{"session":{"oauth_token":"fresh-web"}}}];</script>""")
            } else response(it, 200, """{"id":123}""")
        }
        val client = authenticatedClient(refresh) { response(it, if (it.header("Authorization") == "OAuth fresh-web") 200 else 401) }
        client.newCall(Request.Builder().url("https://api-v2.soundcloud.com/me").build()).execute().use { assertEquals(200, it.code) }
        assertEquals("fresh-web", tokens.accessToken)
        assertTrue(tokens.webCookies!!.contains("oauth_token=fresh-web"))
        assertEquals(2, refreshRequests.size)
    }

    @Test fun doesNotRestoreSessionAfterLogoutDuringRefresh() = runBlocking {
        tokens.save(TokenResponse("expired", "refresh", 3600, null))
        val refresh = fakeClient {
            tokens.clear()
            response(it, 200, """{"access_token":"fresh","expires_in":3600}""")
        }
        assertNull(SessionRefresher(tokens, refresh).refresh("expired", clientId))
        assertNull(tokens.accessToken)
        assertFalse(tokens.isLoggedIn.value)
    }

    @Test fun refreshesBlankClientIdBeforeFirstApiRequest() {
        ids.setManualOverride("")
        val client = authenticatedClient(fakeClient { response(it, 500) }) { response(it, 200) }
        client.newCall(Request.Builder().url("https://api-v2.soundcloud.com/tracks/123").build()).execute().close()
        assertEquals(clientId, requests.single().url.queryParameter("client_id"))
    }

    @Test fun leavesExternalRequestsUntouched() {
        val client = authenticatedClient(fakeClient { response(it, 500) }) { response(it, 200) }
        client.newCall(Request.Builder().url("https://cdn.example/audio.mp3").build()).execute().close()
        assertNull(requests.single().url.queryParameter("client_id"))
        assertNull(requests.single().header("Authorization"))
    }

    private fun authenticatedClient(refresh: OkHttpClient, handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(ids, tokens, DataDomeChallenge(tokens), SessionRefresher(tokens, refresh)))
            .addInterceptor { chain -> requests.add(chain.request()); handler(chain.request()) }
            .build()

    private fun fakeClient(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { handler(it.request()) }.build()

    private fun response(request: Request, code: Int, body: String = ""): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test").body(body.toResponseBody()).build()
}
