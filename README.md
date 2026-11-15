# K56

[![Android](https://github.com/HochleistungsChilla/K56/actions/workflows/android.yml/badge.svg)](https://github.com/HochleistungsChilla/K56/actions/workflows/android.yml)
[![Lizenz: GPL-3.0-or-later](https://img.shields.io/badge/Lizenz-GPL--3.0--orlater-blue.svg)](LICENSE)

Quelloffener Android-Musikplayer als **Cleanroom-Neuentwicklung** mit
Multi-Backend-Architektur. Kein Code, keine Grafiken und keine Marke von
Symfonium wurden übernommen.

**Status:** 0.1.0 — lauffähiger Multi-Server-Client mit Bibliothek, Suche,
Hintergrundwiedergabe und LAN/WAN-Routing.

**K56** · **Domain:** `k56.wtf` (geplant) · **Theme:** Monochrom · **Easter Egg:** ATDT  
Claim: „56k für die Ohren.“

## Plattform

| Anforderung | Wert |
|---|---|
| Android | **9 (API 28) oder neuer** |
| Jellyfin | **10.10.7 oder neuer** (harte Prüfung beim Login) |
| Build | JDK 17+ (getestet mit JDK 21), Android SDK 35 |

## Unterstützte Quellen

| Quelle | Status | Anmeldung |
|---|---|---|
| **Jellyfin** | ✅ | LAN/Internet-Adresse, Benutzer/Passwort, Quick Connect, UDP-Discovery |
| **Emby** | ✅ | LAN/Internet-Adresse, Benutzer/Passwort |
| **Subsonic** | ✅ | Adresse, Benutzer/Passwort (Salt+MD5-Token) |
| **Navidrome** | ✅ | wie Subsonic |
| **OpenSubsonic** | ✅ | wie Subsonic |
| **Plex** | ✅ | PIN auf `plex.tv/link`, Server-Discovery über plex.tv |
| **Audiobookshelf** | ✅ | Adresse, Benutzer/Passwort; Bücher → Alben, Kapitel → Tracks |
| **Kodi** | ✅ | JSON-RPC, optional HTTP-Basic-Auth |
| **Lokale Dateien** | ✅ | MediaStore-Freigabe (Android 9/13+ Berechtigungen) |
| **WebDAV** | ✅ | Adresse, optional Basic Auth; Ordner-Scan ohne Tag-/Cover-Lesen |
| SMB (v2/v3) | ⬜ geplant | Datei-Backend |
| Google Drive / OneDrive / Dropbox / Box / pCloud | ⬜ geplant | OAuth-Backend |

Die Adapter liegen hinter `MediaRepository`; weitere Quellen ändern nur die
Datenschicht, nicht die UI.

## Funktionen (0.1.0)

- Bibliothek: Alben, Interpreten, Titel (Grid/Liste)
- Suche über Alben, Interpreten und Titel
- Album-/Interpreten-Detailseiten
- Wiedergabe über **Media3/ExoPlayer** mit MediaSession, Notification und
  System-Mediensteuerung
- Direct Play für gängige Container; optionales Transcoding
  (Jellyfin: AAC/HLS, Subsonic: MP3)
- Playback-Reporting: Jellyfin (Position/Resume), Plex (Timeline),
  Audiobookshelf (Fortschritt), Subsonic (Scrobble)
- **LAN/Internet-Routing ohne Standortberechtigung**
- Material 3, Dark/Light, eigenes Icon

## LAN/Internet-Routing

Pro Server können zwei Adressen hinterlegt werden:

- **LAN** z. B. `http://192.168.1.10:8096`
- **Internet** z. B. `https://music.example.com`

Auswahl zur Laufzeit:

| Netz | Verhalten |
|---|---|
| WLAN / Ethernet | LAN-Adresse, wenn ein kurzer Erreichbarkeits-Probe gelingt; sonst Internet |
| Mobilfunk | immer Internet-Adresse |
| offline | letzte bekannte Adresse (Fehler wird sauber gemeldet) |

Die Erkennung nutzt ausschließlich `ConnectivityManager`-Transporttypen
(Wi-Fi/Cellular/Ethernet). **Keine SSID, keine Standortberechtigung.**
Optional lässt sich „Externe Adresse bevorzugen“ aktivieren.

Jellyfin-Server im LAN werden zusätzlich per UDP-Broadcast auf Port 7359
gefunden (`who is JellyfinServer?`).

## Architektur

```
app/src/main/java/app/k56/player/
├── core/model/          Domänenmodelle + BackendKind
├── core/net/            NetworkMonitor, EndpointResolver, URL-Normalisierung
├── data/                SessionStore, SettingsStore, MediaRepository, Router
├── data/jellyfin/       Jellyfin/Emby: API, DTOs, Discovery
├── data/subsonic/       Subsonic/OpenSubsonic/Navidrome
├── data/plex/           Plex.tv-PIN + PMS-API
├── data/abs/            Audiobookshelf
├── data/kodi/           Kodi JSON-RPC
├── data/local/          MediaStore
├── playback/            MediaSessionService, ExoPlayer, MediaController
├── di/                  Manueller AppContainer
└── ui/                  Compose: Theme, Navigation, Screens, Components
```

Bewusste Entscheidungen: kein Hilt/Koin, kein Room im MVP, Retrofit +
kotlinx.serialization für Jellyfin, schlanke eigene Clients für die übrigen
Protokolle.

## Build

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # Versions-/Routing-Logik
```

## Bekannte Grenzen

- Subsonic hat keinen „alle Titel“-Endpoint; die Titel-Liste nutzt
  `getRandomSongs` und sortiert lokal. Ein lokaler Sync-Index ist geplant.
- Kodi löst Wiedergabe-URLs pro Queue-Eintrag über `Files.PrepareDownload`
  auf; sehr große Queues starten dadurch langsamer.
- WebDAV liest Metadaten aus Ordner-/Dateinamen (kein TagLib, keine Cover);
  der Scan ist auf 300 Ordner/5.000 Dateien begrenzt.
- SMB und Cloud-Laufwerke sind noch nicht implementiert (siehe
  [docs/ROADMAP.md](docs/ROADMAP.md)).
- Tokens liegen in privaten SharedPreferences; Keystore-Verschlüsselung ist
  als Härtung vorgesehen.

## Rechtliches

K56 ist eine unabhängige Reimplementierung öffentlich dokumentierter
Schnittstellen. Der Prozess ist in [docs/CLEANROOM.md](docs/CLEANROOM.md)
festgehalten. „Symfonium“ ist eine Marke des jeweiligen Inhabers; dieses
Projekt steht in keiner Verbindung dazu.

## Mitmachen

Pull Requests sind willkommen — Regeln und Build-Anleitung stehen in
[CONTRIBUTING.md](CONTRIBUTING.md).

## Lizenz

**GPL-3.0-or-later** — siehe [LICENSE](LICENSE).

Forken ist ausdrücklich erwünscht: klonen, ändern, eigenen Namen geben,
eigenes Theme bauen, forken, Pull Requests schicken. Einzige Bedingung der
GPL ist, dass abgeleitete Werke beim Weiterverteilen ebenfalls unter der
GPL stehen und die Quelle offen bleibt. Wer K56 forkt, muss es also nicht
„K56“ nennen — nur offen bleiben.
