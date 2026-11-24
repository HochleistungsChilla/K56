package app.k56.player.core.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Thrown when a server address would send credentials or media over an
 * unencrypted connection to a host that is not on the local network.
 */
class InsecureTransportException(
    val host: String,
) : Exception("HTTPS nötig: Dieser Host ist nicht im lokalen Netz.")

/**
 * Cleartext policy for user-entered server addresses.
 *
 * Self-hosted Jellyfin usually runs plain HTTP on the LAN, so `http` stays
 * allowed there. Anything else has to use `https`: a public host over plain
 * HTTP would expose the password during login and the token afterwards.
 *
 * "Local" means: loopback, RFC 1918, IPv6 link-local or unique-local, `*.local`,
 * or a single-label name that can only come from local DNS or mDNS.
 */
object HostPolicy {

    fun isLocalHost(rawHost: String): Boolean {
        val host = rawHost.trim().lowercase().removeSurrounding("[", "]")
        if (host.isEmpty()) return false
        if (host == "localhost" || host.endsWith(".localhost")) return true
        if (host == "::1") return true
        if (host.endsWith(".local")) return true

        parseIpv4(host)?.let { return isPrivateIpv4(it) }
        if (host.contains(':')) return isLocalIpv6(host)

        // A single label ("nas", "jellyfin") cannot be a public DNS name.
        return !host.contains('.')
    }

    /** True when [baseUrl] is plain HTTP to a local host — the UI should warn about that. */
    fun isCleartextLocal(baseUrl: String): Boolean {
        val url = withScheme(baseUrl).toHttpUrlOrNull() ?: return false
        return url.scheme.equals("http", ignoreCase = true) && isLocalHost(url.host)
    }

    /**
     * @throws InsecureTransportException when the scheme is `http` and the host
     *   is not local.
     */
    fun requireAllowed(baseUrl: String) {
        val url = withScheme(baseUrl).toHttpUrlOrNull() ?: return
        if (url.scheme.equals("https", ignoreCase = true)) return
        if (isLocalHost(url.host)) return
        throw InsecureTransportException(url.host)
    }

    private fun withScheme(raw: String): String {
        val value = raw.trim()
        return if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            value
        } else {
            "http://$value"
        }
    }

    private fun parseIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val values = IntArray(4)
        for (i in 0 until 4) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            values[i] = value
        }
        return values
    }

    private fun isPrivateIpv4(octets: IntArray): Boolean {
        val a = octets[0]
        val b = octets[1]
        return when {
            a == 10 -> true
            a == 127 -> true
            a == 192 && b == 168 -> true
            a == 172 && b in 16..31 -> true
            a == 169 && b == 254 -> true // link-local
            else -> false
        }
    }

    private fun isLocalIpv6(host: String): Boolean {
        if (host == "::1") return true
        val first = host.substringBefore(':')
        if (first.isEmpty()) return false
        val value = first.toIntOrNull(16) ?: return false
        // fe80::/10 link-local, fc00::/7 unique local
        return (value and 0xffc0) == 0xfe80 || (value and 0xfe00) == 0xfc00
    }
}
