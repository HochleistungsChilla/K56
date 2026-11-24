package app.k56.player.core.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `http` stays possible on a LAN, because self-hosted servers usually have no
 * certificate. Everything else has to be `https`.
 */
class HostPolicyTest {

    private val localHosts = listOf(
        "192.168.0.93",
        "10.1.2.3",
        "172.16.0.9",
        "172.31.255.1",
        "127.0.0.1",
        "169.254.10.20",
        "localhost",
        "jellyfin.local",
        "::1",
        "[::1]",
        "fe80::1",
        "fc00::1",
        "nas",
    )

    private val publicHosts = listOf(
        "jellyfin.example.com",
        "music.example.org",
        "8.8.8.8",
        "172.32.0.1",
        "203.0.113.5",
    )

    @Test
    fun localHostsAreRecognised() {
        localHosts.forEach { host ->
            assertTrue("expected local: $host", HostPolicy.isLocalHost(host))
        }
    }

    @Test
    fun publicHostsAreNotLocal() {
        publicHosts.forEach { host ->
            assertFalse("expected public: $host", HostPolicy.isLocalHost(host))
        }
    }

    @Test
    fun httpIsAllowedOnTheLan() {
        HostPolicy.requireAllowed("http://192.168.0.93:8096")
        HostPolicy.requireAllowed("192.168.0.93:8096")
        HostPolicy.requireAllowed("http://localhost:8096")
        HostPolicy.requireAllowed("http://jellyfin.local:8096")
        HostPolicy.requireAllowed("http://127.0.0.1:8096")
        HostPolicy.requireAllowed("http://[::1]:8096")
    }

    @Test
    fun httpIsRejectedForPublicHosts() {
        val error = assertThrows(InsecureTransportException::class.java) {
            HostPolicy.requireAllowed("http://jellyfin.example.com")
        }
        assertTrue(error.message!!.contains("HTTPS"))
        assertTrue(error.host == "jellyfin.example.com")

        assertThrows(InsecureTransportException::class.java) {
            HostPolicy.requireAllowed("http://8.8.8.8:8096")
        }
    }

    @Test
    fun httpsIsAlwaysAllowed() {
        HostPolicy.requireAllowed("https://jellyfin.example.com")
        HostPolicy.requireAllowed("https://192.168.0.93:8920")
    }

    @Test
    fun cleartextFlagFollowsTheScheme() {
        assertTrue(HostPolicy.isCleartextLocal("http://192.168.0.93:8096"))
        assertFalse(HostPolicy.isCleartextLocal("https://192.168.0.93:8920"))
        assertFalse(HostPolicy.isCleartextLocal("http://jellyfin.example.com"))
    }
}
