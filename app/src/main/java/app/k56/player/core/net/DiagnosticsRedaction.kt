package app.k56.player.core.net

/**
 * Redaction for the verbose diagnostics mode.
 *
 * The normal buffer never stores a query value. Verbose mode needs values to
 * make a `400` readable ("which SortBy was rejected?"), so it keeps them - but
 * never for anything that is a credential, and never longer than a screen line.
 */
object DiagnosticsRedaction {

    /** Parameter names whose value is a credential, whatever they belong to. */
    private val SECRET_NAMES = setOf(
        "api_key", "apikey", "token", "access_token", "x-emby-token",
        "pw", "password", "secret", "apitoken",
    )

    fun isSecret(name: String): Boolean = name.lowercase() in SECRET_NAMES

    /**
     * `IncludeItemTypes=Audio&SortBy=&api_key=***`, truncated to [maxLength].
     * A secret name keeps its name and loses its value; everything else is
     * shown as sent.
     */
    fun format(pairs: List<Pair<String, String>>, maxLength: Int = 400): String {
        if (pairs.isEmpty()) return ""
        return pairs.joinToString("&") { (name, value) ->
            if (isSecret(name)) "$name=***" else "$name=${value.take(MAX_VALUE_LENGTH)}"
        }.take(maxLength)
    }

    private const val MAX_VALUE_LENGTH = 120
}
