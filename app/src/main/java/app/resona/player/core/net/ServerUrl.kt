package app.resona.player.core.net

import java.net.URI

object ServerUrl {

    /**
     * Accepts "host:8096", a trailing slash, or a pasted web UI URL
     * ("https://host/jellyfin/web/index.html").
     */
    fun normalize(raw: String): String {
        var value = raw.trim()
        require(value.isNotEmpty()) { "Serveradresse fehlt." }
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            value = "http://$value"
        }
        value = value.trimEnd('/')
        value = value.removeSuffix("/web/index.html")
        value = value.removeSuffix("/web")
        value = value.trimEnd('/')
        return "$value/"
    }

    fun host(raw: String): String = runCatching {
        URI(normalize(raw)).host.orEmpty()
    }.getOrDefault(raw)
}
