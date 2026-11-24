package app.k56.player.core.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves the redaction on the wire, not just the field list: a request carries
 * an API key in the query and a token in a header, and neither may appear in
 * the buffer.
 */
class DiagnosticsInterceptorTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .addInterceptor(DiagnosticsInterceptor())
        .build()

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

    @Test
    fun theQueryStringIsNeverStored() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.newCall(
            Request.Builder()
                .url(server.url("/Items?api_key=TOPSECRET&userId=user-42"))
                .header("X-Emby-Token", "TOKEN-ABC")
                .header("Authorization", "MediaBrowser Token=\"TOKEN-ABC\"")
                .build(),
        ).execute().close()

        val entry = DiagnosticsStore.entries.value.single()
        assertEquals("/Items", entry.path)
        assertEquals(200, entry.status)
        assertEquals("GET", entry.method)

        val dump = DiagnosticsStore.entries.value.joinToString { "${it.method} ${it.path} ${it.status} ${it.errorKind}" }
        assertFalse("query value leaked: $dump", dump.contains("TOPSECRET"))
        assertFalse("header value leaked: $dump", dump.contains("TOKEN-ABC"))
        assertFalse("user id leaked: $dump", dump.contains("user-42"))
    }

    @Test
    fun aFailingStatusIsRecordedAsHttp() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        client.newCall(Request.Builder().url(server.url("/Users/Me")).build()).execute().close()

        val entry = DiagnosticsStore.entries.value.single()
        assertEquals(401, entry.status)
        assertEquals(DiagnosticsErrorKind.HTTP, entry.errorKind)
    }

    @Test
    fun aFailedRequestKeepsTheNamesButNotTheValues() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))

        client.newCall(
            Request.Builder()
                .url(server.url("/Items?api_key=TOPSECRET&IncludeItemTypes=Audio&SortBy="))
                .build(),
        ).execute().close()

        val entry = DiagnosticsStore.entries.value.single()
        assertEquals(400, entry.status)
        assertEquals(listOf("IncludeItemTypes", "SortBy", "api_key"), entry.queryNames)
        val dump = entry.queryNames.joinToString("&")
        assertFalse("query value leaked: $dump", dump.contains("TOPSECRET"))
        assertFalse("query value leaked: $dump", dump.contains("Audio"))
    }

    @Test
    fun aSuccessfulRequestStoresNoParameterNames() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.newCall(
            Request.Builder().url(server.url("/Items?api_key=TOPSECRET&Recursive=true")).build(),
        ).execute().close()

        val entry = DiagnosticsStore.entries.value.single()
        assertEquals(200, entry.status)
        assertTrue(entry.queryNames.isEmpty())
    }

    @Test
    fun aDeadHostIsRecordedAsIo() {
        server.shutdown()

        runCatching {
            client.newCall(Request.Builder().url("http://127.0.0.1:1/Items").build()).execute().close()
        }

        val entry = DiagnosticsStore.entries.value.single()
        assertEquals(null, entry.status)
        assertTrue(entry.errorKind != null)
        assertEquals("/Items", entry.path)
    }
}
