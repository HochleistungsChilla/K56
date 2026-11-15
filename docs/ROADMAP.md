# Feature-Parität & Roadmap

Referenz: öffentlich dokumentierte Funktionen von Symfonium
(symfonium.app, Stand 15.11.2026). „Parität“ heißt funktional vergleichbar,
nicht gestalterisch identisch.

## Quellen

| Quelle | Status |
|---|---|
| Jellyfin / Emby | ✅ |
| Subsonic / OpenSubsonic / Navidrome | ✅ |
| Plex | ✅ |
| Audiobookshelf | ✅ |
| Kodi | ✅ |
| Lokale Dateien (MediaStore) | ✅ |
| SMB (v2/v3) | ⬜ |
| WebDAV | ⬜ |
| Google Drive | ⬜ |
| OneDrive | ⬜ |
| Dropbox | ⬜ |
| Box | ⬜ |
| pCloud | ⬜ |

Datei- und Cloud-Quellen brauchen eigene Datenquellen (SMB-Streaming,
WebDAV-PROPFIND, OAuth-Apps). Die Backend-Schnittstelle ist darauf
vorbereitet; die Adapter folgen.

## Funktionen

| Bereich | Resona 0.1.0 | Nächster Schritt |
|---|---|---|
| Bibliothek (Alben/Interpreten/Titel) | ✅ | Genres, Jahre, Sortierungen |
| Suche | ✅ | Server- und lokale Volltextsuche |
| Wiedergabe | ✅ Media3/ExoPlayer | Gapless, Crossfade |
| Hintergrund + Notification | ✅ | Android Auto |
| Playback-Reporting | ✅ Jellyfin/Plex/ABS/Subsonic | Scrobble-Fehleranzeige |
| LAN/Internet-Routing | ✅ ohne Standort | Manuelle Server-Priorität |
| Offline-Cache / Downloads | ⬜ | Room + Cache-Verzeichnis |
| Playlists | ⬜ | Jellyfin/Subsonic/ABS |
| Smart Playlists | ⬜ | Regel-Engine auf lokaler DB |
| Lyrics (synchronisiert) | ⬜ | LRC + Server-Lyrics |
| Equalizer / AutoEQ / PEQ | ⬜ | Media3-AudioProcessor |
| ReplayGain / Crossfeed | ⬜ | DSP-Kette |
| Hi-Res / DSD / USB-DAC | ⬜ | AudioTrack/USB-Pfad |
| Chromecast / UPnP / Sonos | ⬜ | Cast-SDK / SSDP |
| Android TV | ⬜ | DPAD-Navigation |
| Wear OS | ⬜ | Modul + Offline-Sync |
| Widgets | ⬜ | Glance |
| Themes / Now-Playing-Layouts | ⬜ | Theme-Engine |

## Architektur für die nächsten Schritte

1. **`MediaRepository`** ist die Naht: jeder neue Anbieter implementiert sie,
   der `RepositoryRouter` wählt anhand der Session.
2. **Lokale DB (Room)** für Offline-Metadaten, Cache-Index, Favoriten,
   Playlists und Smart-Playlist-Regeln.
3. **Audio-Pipeline** kapseln: Direct Play → Transcode-Fallback → DSP.
4. **Datei-Backends** (SMB/WebDAV) über eigene `DataSource`-Implementierungen
   für Media3, damit Header/Streams sauber funktionieren.
5. **Cloud** mit OAuth-Client-IDs, die Nutzer selbst registrieren können.

## Nicht-Ziele

- Kein Kopieren von Symfonium-Designs, Assets oder Marke
- Keine Umgehung von Bezahl-/Lizenzlogik
- Keine Telemetrie oder Cloud-Zwischenstation
