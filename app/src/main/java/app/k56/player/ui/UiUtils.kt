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

    is HttpException -> when (code()) {
        400 -> context.getString(R.string.err_request_rejected)
        401, 403 -> context.getString(R.string.err_login_rejected)
        404 -> context.getString(R.string.err_not_found_at_address)
        else -> context.getString(R.string.err_http, code())
    }

    is IOException ->
        context.getString(R.string.err_unreachable)

    else -> message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.err_unknown)
}
