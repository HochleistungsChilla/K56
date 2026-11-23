package app.k56.player.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two failure modes that reached real devices: an exception escaping a
 * coroutine, and a silent request to the wrong host.
 */
class BaseUrlTest {

    @Test
    fun aBareHostGetsHttp() {
        val url = parseBaseUrl("192.168.0.93:8090")
        assertEquals("http", url.scheme)
        assertEquals("192.168.0.93", url.host)
        assertEquals(8090, url.port)
    }

    @Test
    fun aTrailingSlashIsDropped() {
        // HttpUrl's canonical form always ends an empty path with "/".
        assertEquals("http://host:5030/", parseBaseUrl("http://host:5030/").toString())
        assertEquals("https://host/", parseBaseUrl("https://host//").toString())
    }

    @Test
    fun aSubPathSurvives() {
        assertEquals("http://host/sub", parseBaseUrl("host/sub/").toString())
    }

    @Test
    fun schemeWithoutHostIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("http://") }
        assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("https://") }
        assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("") }
        assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("   ") }
    }

    @Test
    fun emptyHostWithPortIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("http://:8090") }
    }

    @Test
    fun aPathAsTheFirstSegmentIsNotAHost() {
        // "http:///api/x" used to parse as host "api" in OkHttp.
        assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("http:///api/x") }
    }

    @Test
    fun aSingleSlashIsRepaired() {
        assertEquals("http://host:5030/", parseBaseUrl("http:/host:5030").toString())
        assertEquals("https://host/", parseBaseUrl("https:/host").toString())
        // Repairing must not invent a host where there is none.
        assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("http:/") }
    }

    @Test
    fun aHostWithASpaceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("http://exa mple.com:8090") }
        assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("192.168.0.9 3:8090") }
    }

    @Test
    fun theMessageSaysWhatIsWrong() {
        val error = assertThrows(IllegalArgumentException::class.java) { parseBaseUrl("http://") }
        assertTrue(error.message!!.contains("host"))
    }
}
