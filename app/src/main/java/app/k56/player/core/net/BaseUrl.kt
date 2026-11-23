package app.k56.player.core.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Parses a base URL that a human typed into a settings field.
 *
 * Two traps this exists for, both seen on real devices:
 *
 * 1. `"http://".toHttpUrl()` throws `IllegalArgumentException`, which is not one
 *    of the app's own exception types and used to escape a coroutine.
 * 2. Concatenating a path first turns `"http://"` into `"http:///api/…"`, and
 *    OkHttp reads that as host `api` — no exception, just a request to the
 *    wrong host. The same happens for `"http://:8090"` (empty host).
 *
 * Append paths with `newBuilder().addPathSegments(…)` on the result, never with
 * string concatenation.
 *
 * @throws IllegalArgumentException when the text has no usable host. Callers
 *   translate this into their own error type and keep it as the cause.
 */
fun parseBaseUrl(raw: String): HttpUrl {
    val trimmed = raw.trim()
    require(trimmed.isNotEmpty()) { "URL is empty" }
    // "http:/host" and "http:host" are typos people actually make. Repair the
    // separator instead of turning the scheme into a host name further down.
    val repaired = trimmed.replace(SCHEME_SEPARATOR, "$1://")
    val withScheme = if (repaired.contains("://")) repaired else "http://$repaired"
    val schemeEnd = withScheme.indexOf("://") + 3
    if (schemeEnd < 3) require(false) { "URL has no scheme" }

    // Everything after the scheme must start with a host, not with a slash and
    // not with a port separator.
    val host = withScheme.substring(schemeEnd)
        .substringBefore('/')
        .substringBefore(':')
    require(host.isNotBlank()) { "URL has no host" }

    // A single trailing slash is harmless; "http://" must not lose its last
    // character and turn into "http:".
    val withoutTrailingSlash = withScheme.trimEnd('/')
    require(withoutTrailingSlash.length > schemeEnd) { "URL has no host" }
    return withoutTrailingSlash.toHttpUrl()
}

/**
 * Matches `http:` and `http:/` at the start — but never `http://`, so a
 * genuinely broken `http:///path` keeps failing instead of becoming host "path".
 */
private val SCHEME_SEPARATOR = Regex("^(https?):(?!//)/?", RegexOption.IGNORE_CASE)
