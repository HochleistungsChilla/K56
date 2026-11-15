package app.nadelton.player.data.jellyfin

import app.nadelton.player.core.model.DiscoveredServer
import app.nadelton.player.core.net.ServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Jellyfin's documented LAN discovery: a UDP broadcast to port 7359 with
 * "who is JellyfinServer?"; servers answer with a small JSON object.
 *
 * This needs no location permission and no SSID access.
 */
class JellyfinDiscovery {

    suspend fun discover(timeoutMs: Long = 1_800L): List<DiscoveredServer> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<DiscoveredServer>()
            val socket = runCatching { DatagramSocket().apply { broadcast = true; soTimeout = 300 } }
                .getOrNull() ?: return@withContext emptyList()

            try {
                val message = "who is JellyfinServer?".toByteArray()
                val broadcast = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(message, message.size, broadcast, DISCOVERY_PORT))

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length)
                        parse(text)?.let { results += it }
                    } catch (_: SocketTimeoutException) {
                        // keep listening until the deadline
                    }
                }
            } catch (_: Exception) {
                // broadcast can be blocked by the network; manual entry remains
            } finally {
                runCatching { socket.close() }
            }

            results.distinctBy { it.url }
        }

    private fun parse(text: String): DiscoveredServer? = runCatching {
        val json = Json.parseToJsonElement(text).jsonObject
        val address = json["Address"]?.jsonPrimitive?.content
            ?: json["address"]?.jsonPrimitive?.content
            ?: return null
        if (address.isBlank()) return null
        val name = json["Name"]?.jsonPrimitive?.content
            ?: json["name"]?.jsonPrimitive?.content
            ?: ServerUrl.host(address)
        DiscoveredServer(name = name, url = ServerUrl.normalize(address))
    }.getOrNull()

    private companion object {
        const val DISCOVERY_PORT = 7359
    }
}
