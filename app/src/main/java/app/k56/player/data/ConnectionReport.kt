package app.k56.player.data

/**
 * Outcome of [MediaRepository.testConnection]. Never thrown: the caller gets a
 * report and decides what to show.
 */
data class ConnectionReport(
    val reachable: Boolean,
    val serverName: String?,
    val serverVersion: String?,
    val authOk: Boolean,
    val latencyMs: Long,
    val error: String?,
    val errorKind: ConnectionErrorKind?,
)

enum class ConnectionErrorKind { TIMEOUT, DNS, TLS, INSECURE, AUTH, HTTP, NOT_CONFIGURED, UNSUPPORTED, IO }
