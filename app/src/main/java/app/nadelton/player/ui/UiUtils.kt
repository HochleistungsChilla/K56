package app.nadelton.player.ui

import app.nadelton.player.data.NotAuthenticatedException
import app.nadelton.player.data.subsonic.SubsonicException
import app.nadelton.player.data.jellyfin.QuickConnectDisabledException
import app.nadelton.player.data.jellyfin.QuickConnectTimeoutException
import app.nadelton.player.data.jellyfin.UnsupportedServerException
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

fun Throwable.userMessage(): String = when (this) {
    is UnsupportedServerException ->
        message ?: "Dieser Jellyfin-Server ist zu alt für Nadelton."

    is NotAuthenticatedException ->
        "Sitzung abgelaufen. Bitte neu anmelden."

    is SubsonicException -> when (code) {
        40 -> "Anmeldung abgelehnt. Benutzername und Passwort prüfen."
        41 -> "Der Server erlaubt keine Token-Anmeldung. Bitte Passwort-Login aktivieren."
        50 -> "Keine Berechtigung für diese Aktion."
        else -> message ?: "Subsonic-Fehler (Code $code)."
    }

    is QuickConnectDisabledException ->
        "Quick Connect ist auf diesem Server deaktiviert."

    is QuickConnectTimeoutException ->
        "Quick Connect wurde nicht bestätigt. Bitte erneut versuchen."

    is HttpException -> when (code()) {
        400 -> "Der Server hat die Anfrage abgelehnt."
        401, 403 -> "Anmeldung abgelehnt. Benutzername und Passwort prüfen."
        404 -> "Kein Jellyfin-Server unter dieser Adresse gefunden."
        else -> "Serverfehler (HTTP ${code()})."
    }

    is IOException ->
        "Server nicht erreichbar. Adresse, Netzwerk und Port prüfen."

    else -> message?.takeIf { it.isNotBlank() } ?: "Unbekannter Fehler."
}
