package app.k56.player.data.audiomuse

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Runs the client against a local stub of the AudioMuse API. The stub answers
 * with the shapes documented upstream plus extra fields, to prove the parsing
 * is tolerant; a wrong token and a vanished instance are covered as well.
 *
 * Plain sockets instead of a test web server: no extra dependency, and the
 * wire format is simple enough to see in one place.
 */
class AudioMuseApiTest {

    private lateinit var server: ServerSocket
    private val lastSearchBody = AtomicReference<String?>(null)
    private var worker: Thread? = null

    private val statsBody = """
        {"clap_enabled": true, "num_embeddings": 62762, "last_refresh": "2026-11-21T22:04:11",
         "extra_field": {"nested": [1, 2]}}
    """.trimIndent()

    private val searchBody = """
        {"query": "calm piano", "count": 2, "results": [
          {"item_id": "abc123", "title": "Nachtstück", "author": "Someone",
           "album": "Piano", "similarity": 0.83, "unexpected": true},
          {"item_id": "def456", "title": "Morgen", "author": "",
           "similarity": 0.41}
        ]}
    """.trimIndent()

    private val similarBody = """
        [{"artist": "Someone", "artist_id": "art1", "divergence": 0.12},
         {"artist": "Other", "artist_id": null}]
    """.trimIndent()

    @Before
    fun start() {
        server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        worker = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                runCatching { handle(socket) }
            }
        }
    }

    @After
    fun stop() {
        runCatching { server.close() }
        worker?.join(1000)
    }

    private fun handle(socket: java.net.Socket) = socket.use { s ->
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine().orEmpty()
        val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?').orEmpty()
        var authorization: String? = null
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val name = line.substringBefore(':').trim()
            val value = line.substringAfter(':').trim()
            when {
                name.equals("Authorization", ignoreCase = true) -> authorization = value
                name.equals("Content-Length", ignoreCase = true) -> contentLength = value.toIntOrNull() ?: 0
            }
        }
        val body = if (contentLength > 0) {
            val buffer = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = reader.read(buffer, read, contentLength - read)
                if (n < 0) break
                read += n
            }
            String(buffer, 0, read)
        } else {
            ""
        }

        val authOk = authorization == "Bearer good-token"
        val (code, payload) = when {
            !authOk -> 401 to
                """{"error":"Unauthorized","error_class":"Authentication Required","error_code":1005}"""
            path == "/api/clap/stats" -> 200 to statsBody
            path == "/api/clap/search" -> {
                lastSearchBody.set(body)
                200 to searchBody
            }
            path == "/api/clap/search_by_song" -> 200 to searchBody
            path == "/api/similar_artists" -> 200 to similarBody
            else -> 404 to """{"error": "not found"}"""
        }
        val bytes = payload.toByteArray()
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        s.getOutputStream().apply {
            write(head.toByteArray())
            write(bytes)
            flush()
        }
    }

    private fun baseUrl() = "http://127.0.0.1:${server.localPort}"

    private fun api(token: String = "good-token") =
        AudioMuseApi(AudioMuseConfig(baseUrl = baseUrl(), token = token))

    @Test
    fun statsAreReadFromUnknownExtraFields() = runBlocking {
        val stats = api().stats()
        assertEquals(true, stats.clapEnabled)
        assertEquals(62762, stats.embeddings)
        assertEquals("2026-11-21T22:04:11", stats.lastRefresh)
    }

    @Test
    fun wrongTokenIsAnAuthFailure() = runBlocking {
        try {
            api(token = "wrong").stats()
            fail("expected AudioMuseException")
        } catch (e: AudioMuseException) {
            assertTrue(e.isAuthFailure)
            assertEquals(401, e.statusCode)
            assertTrue(e.message!!.contains("token missing or wrong"))
        }
    }

    @Test
    fun searchParsesResultsAndSendsTheLimit() = runBlocking {
        val tracks = api().search("calm piano", limit = 2)
        assertEquals(2, tracks.size)
        assertEquals("abc123", tracks[0].itemId)
        assertEquals("Nachtstück", tracks[0].title)
        assertEquals(0.83, tracks[0].similarity!!, 0.0001)
        assertNull(tracks[1].album)
        assertEquals(0.41, tracks[1].similarity!!, 0.0001)
        val sent = lastSearchBody.get().orEmpty()
        assertTrue(sent.contains("\"query\":\"calm piano\""))
        assertTrue(sent.contains("\"limit\":2"))
    }

    @Test
    fun searchBySongUsesTheItemId() = runBlocking {
        val tracks = api().searchBySong("abc123", limit = 5)
        assertEquals("abc123", tracks[0].itemId)
    }

    @Test
    fun similarArtistsReadsPlainArray() = runBlocking {
        val artists = api().similarArtists("Someone", limit = 2)
        assertEquals(2, artists.size)
        assertEquals("art1", artists[0].artistId)
        assertNull(artists[1].artistId)
        assertNull(artists[1].divergence)
    }

    @Test
    fun rejectionsKeepTheServerTextAndTheStatus() {
        val mapped = mappedFailure(api(), 400, """{"error": "Missing \"query\" in request body"}""")
        assertEquals(400, mapped.statusCode)
        assertTrue(mapped.message!!.contains("Missing \"query\""))
    }

    @Test
    fun missingCacheIsReportedAsUnavailable() {
        val mapped = mappedFailure(api(), 503, """{"error_message": "CLAP cache not loaded."}""")
        assertEquals(503, mapped.statusCode)
        assertTrue(mapped.message!!.contains("CLAP cache not loaded"))
    }

    @Test
    fun emptyTokenIsRejectedBeforeAnyRequest() = runBlocking {
        try {
            api(token = "").stats()
            fail("expected AudioMuseException")
        } catch (e: AudioMuseException) {
            assertTrue(e.message!!.contains("token is empty"))
            assertNull(e.statusCode)
        }
    }

    @Test
    fun unreachableInstanceSaysSo() = runBlocking {
        val dead = AudioMuseApi(AudioMuseConfig("http://127.0.0.1:1", "good-token"))
        try {
            dead.stats()
            fail("expected AudioMuseException")
        } catch (e: AudioMuseException) {
            assertTrue(e.message!!.contains("not reachable"))
        }
    }

    private fun mappedFailure(api: AudioMuseApi, code: Int, body: String): AudioMuseException {
        val method = AudioMuseApi::class.java.getDeclaredMethod(
            "failure",
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(api, code, body) as AudioMuseException
    }
}
