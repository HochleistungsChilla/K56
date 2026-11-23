package app.k56.player.data.acquisition.clients

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A broken URL must be a DownloadClientException, never an
 * IllegalArgumentException escaping the coroutine — and never a quiet request
 * to a host invented from a typo.
 */
class DownloadClientUrlTest {

    private val brokenValues = listOf("http://", "https://", "http://:5030", "http:///api", "192.168.0.9 3:5030")

    @Test
    fun slskdReportsABrokenUrl() = runBlocking {
        brokenValues.forEach { raw ->
            val client = SlskdClient(SlskdConfig(baseUrl = raw, apiKey = "key"))
            try {
                client.test()
                fail("expected DownloadClientException for $raw")
            } catch (e: DownloadClientException) {
                assertTrue("for $raw: ${e.message}", e.message!!.contains("URL is not valid"))
            }
        }
    }

    @Test
    fun sabnzbdReportsABrokenUrl() = runBlocking {
        brokenValues.forEach { raw ->
            val client = SabnzbdClient(SabnzbdConfig(baseUrl = raw, key = "key"))
            try {
                client.test()
                fail("expected DownloadClientException for $raw")
            } catch (e: DownloadClientException) {
                assertTrue("for $raw: ${e.message}", e.message!!.contains("URL is not valid"))
            }
        }
    }

    @Test
    fun qbittorrentReportsABrokenUrl() = runBlocking {
        brokenValues.forEach { raw ->
            val client = QbittorrentClient(QbittorrentConfig(baseUrl = raw, username = "u", passwd = "p"))
            try {
                client.test()
                fail("expected DownloadClientException for $raw")
            } catch (e: DownloadClientException) {
                assertTrue("for $raw: ${e.message}", e.message!!.contains("URL is not valid"))
            }
        }
    }

    @Test
    fun aWorkingUrlStillReachesTheNetworkLayer() = runBlocking {
        // Port 1 refuses the connection: the message must be about reachability,
        // which proves the URL parsed and the request was built.
        val client = SlskdClient(SlskdConfig(baseUrl = "127.0.0.1:1", apiKey = "key"))
        try {
            client.test()
            fail("expected DownloadClientException")
        } catch (e: DownloadClientException) {
            assertTrue(e.message!!.contains("not reachable"))
        }
    }
}
