package app.k56.player.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
    OFFLINE,
}

data class NetworkStatus(
    val transport: NetworkTransport,
    val online: Boolean,
)

/**
 * Network-type detection without any location permission.
 *
 * SSID-based "home network" detection would require ACCESS_FINE_LOCATION on
 * Android 10+, so K56 deliberately routes on transport type only: Wi-Fi /
 * Ethernet may reach the LAN address, cellular goes straight to the external
 * address.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    init {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _status.value = readStatus()
                    }

                    override fun onLost(network: Network) {
                        _status.value = readStatus()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        _status.value = readStatus()
                    }
                },
            )
        }
    }

    /**
     * Re-reads the status on demand. Network callbacks can lag or be missed
     * (for example when Wi-Fi disappears on an emulator), so callers that are
     * about to depend on connectivity refresh first.
     */
    fun refresh(): NetworkStatus {
        val current = readStatus()
        if (current != _status.value) _status.value = current
        return current
    }

    private fun readStatus(): NetworkStatus {
        val active = connectivityManager.activeNetwork
            ?: return NetworkStatus(NetworkTransport.OFFLINE, online = false)
        val capabilities = connectivityManager.getNetworkCapabilities(active)
            ?: return NetworkStatus(NetworkTransport.OFFLINE, online = false)

        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OTHER
        }
        val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return NetworkStatus(transport, online)
    }
}
