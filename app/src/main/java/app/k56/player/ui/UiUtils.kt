package app.k56.player.ui

import android.content.Context
import app.k56.player.R
import app.k56.player.data.NotAuthenticatedException
import app.k56.player.data.subsonic.SubsonicException
import app.k56.player.data.jellyfin.QuickConnectDisabledException
import app.k56.player.data.jellyfin.QuickConnectTimeoutException
import app.k56.player.data.jellyfin.UnsupportedServerException
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * Maps a thrown exception to a localized, user-facing message. Pass the
 * application context; the message catalog lives in `res/values/strings.xml`
 * (English) and `res/values-de/strings.xml` (German).
 */
fun Throwable.userMessage(context: Context): String = when (this) {
    is UnsupportedServerException ->
        message ?: context.getString(R.string.err_server_too_old)

    is NotAuthenticatedException ->
        context.getString(R.string.err_session_expired)

    is SubsonicException -> when (code) {
        40 -> context.getString(R.string.err_login_rejected)
        41 -> context.getString(R.string.err_token_login_disabled)
        50 -> context.getString(R.string.err_permission)
        else -> message ?: context.getString(R.string.err_subsonic, code)
    }

    is QuickConnectDisabledException ->
        context.getString(R.string.err_quick_connect_disabled)

    is QuickConnectTimeoutException ->
        context.getString(R.string.err_quick_connect_unconfirmed)

    is HttpException -> when {
        code() == 401 -> context.getString(R.string.err_credentials)
        code() == 403 -> context.getString(R.string.err_forbidden)
        code() == 404 -> context.getString(R.string.err_route_missing)
        code() == 400 -> context.getString(R.string.err_request_rejected)
        code() >= 500 -> {
            // The server often explains itself; keep the body short and readable.
            val detail = runCatching {
                response()?.errorBody()?.string()?.trim()?.take(160)
            }.getOrNull()
            when {
                detail.isNullOrBlank() -> context.getString(R.string.err_http, code())
                else -> context.getString(R.string.err_http_detail, code(), detail)
            }
        }
        else -> context.getString(R.string.err_http, code())
    }

    // Cleartext is refused by the platform, not by the server, and says so.
    is java.net.UnknownServiceException ->
        if (message?.contains("CLEARTEXT", ignoreCase = true) == true) {
            context.getString(R.string.err_https_required)
        } else {
            context.getString(R.string.err_unreachable)
        }

    is java.net.SocketTimeoutException -> context.getString(R.string.err_timeout)
    is java.net.UnknownHostException -> context.getString(R.string.err_dns)
    is javax.net.ssl.SSLException -> context.getString(R.string.err_tls)

    is IOException ->
        context.getString(R.string.err_unreachable)

    else -> message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.err_unknown)
}

/** Human readable byte size used by the storage and cache screens. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / 1048576.0
    return if (mb >= 1024.0) String.format("%.1f GB", mb / 1024.0)
    else String.format("%.1f MB", mb)
}
