package app.k56.player.data.jellyfin

import app.k56.player.core.net.DiagnosticsStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Jellyfin 12.4 ignores `X-Emby-Token` and only accepts the token inside the
 * `Authorization` header; older servers use the opposite. Both travel, and the
 * diagnostics buffer must not learn the token in either case.
 */
class JellyfinAuthHeaderTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        DiagnosticsStore.clear()
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
        DiagnosticsStore.clear()
    }

    private fun api(token: String?) = JellyfinApiFactory.create(
        baseUrl = server.url("/").toString(),
        deviceId = "device-1",
        clientVersion = "1.0",
        token = token,
    )

    @Test
    fun theTokenTravelsInTheAuthorizationHeader() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        api("TOKEN-XYZ").me()
        val request = server.takeRequest()

        val authorization = request.getHeader("Authorization").orEmpty()
        assertTrue("Authorization was: $authorization", authorization.contains("Token=\"TOKEN-XYZ\""))
        assertTrue(authorization.startsWith("MediaBrowser "))
        assertTrue(authorization.contains("Client=\"K56\""))
        assertTrue(authorization.contains("DeviceId=\"device-1\""))
    }

    @Test
    fun theLegacyHeaderIsStillSent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        api("TOKEN-XYZ").me()
        val request = server.takeRequest()

        assertEquals("TOKEN-XYZ", request.getHeader("X-Emby-Token"))
        assertTrue(request.getHeader("X-Emby-Authorization").orEmpty().contains("Token=\"TOKEN-XYZ\""))
    }

    @Test
    fun anAnonymousRequestCarriesNoToken() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ServerName\":\"x\"}"))

        api(null).publicSystemInfo()
        val request = server.takeRequest()

        assertNotNull(request.getHeader("Authorization"))
        assertFalse(request.getHeader("Authorization").orEmpty().contains("Token="))
        assertEquals(null, request.getHeader("X-Emby-Token"))
    }

    @Test
    fun theDiagnosticsBufferStillHasNoToken() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        api("TOKEN-XYZ").me()

        val dump = DiagnosticsStore.entries.value.joinToString { "${it.method} ${it.path} ${it.status} ${it.errorKind}" }
        assertFalse("token leaked into the buffer: $dump", dump.contains("TOKEN-XYZ"))
        assertTrue(dump.contains("/Users/Me"))
    }
}
