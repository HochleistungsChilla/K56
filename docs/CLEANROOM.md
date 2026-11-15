# Cleanroom-Prozess

Ziel: ein funktional vergleichbarer Musikplayer, der **ohne Übernahme von
Quellcode, Ressourcen oder geschützten Kennzeichen** entsteht.

## Trennung

| Rolle | Input | Output |
|---|---|---|
| Analyse | Öffentliche Produktdoku, APK-Metadaten, Protokoll-Recherche | Feature-/Schnittstellen-Spezifikation |
| Implementierung | Spezifikation + öffentliche API-Dokumentation | Eigener Kotlin/Compose-Code |

Die Implementierung übernimmt **keinen** Anwendungscode und **keine**
Ressourcen aus der APK. Eine Analyse der APK (Manifest, Versionsdaten,
String-Inventar) dient ausschließlich der Interoperabilität: welche Quellen
und Protokolle unterstützt werden müssen.

## Beobachtete Metadaten der Referenz-APK

- Paket: `app.symfonik.music.player`
- Version: `15.1.0 TV` (`versionCode 227812`)
- minSdk 26, targetSdk 36, compileSdk 37
- 4 ABIs, 13 native Bibliotheken (u. a. FFmpeg-Bibliotheken, TagLib, Zstandard)
- 22 JSON-Layout-Templates für Now-Playing-/App-Styles
- Berechtigungen: Netzwerk, Foreground-Media-Playback, Audio-Lesen, Bluetooth,
  Billing, Ads-Attribution

Daraus abgeleitete, **rein funktionale** Anforderungen: mehrere Quellen,
konfigurierbare Wiedergabe-Ansichten, lokale Dateien, Server-Streaming,
Hintergrundwiedergabe. Es wurden keine Strings, Layouts, Grafiken oder
Codeblöcke übernommen.

## Protokoll-Recherche

Die unterstützten Quellen werden über ihre öffentlichen APIs angebunden:

- Jellyfin/Emby: REST (10.10.x)
- Subsonic/OpenSubsonic/Navidrome: `/rest/*.view` (1.16.1)
- Plex: plex.tv v2 PIN + PMS `/library/*`
- Audiobookshelf: REST `/api/*`
- Kodi: JSON-RPC
- Lokal: Android MediaStore

## Bewusste Nicht-Übernahmen

- Keine Symfonium-Marke, kein Logo, kein Store-Listing-Text
- Keine kopierten Color-Resources, Drawables, JSON-Styles oder Sounds
- Keine Übernahme von Bibliotheksinterna oder Klassennamen
- Keine Umgehung von Lizenz-/Bezahlmechanismen

## Grenzen

Funktionen und Ideen sind urheberrechtlich nicht geschützt; konkrete
Ausdrucksformen schon. Deshalb beschreibt die Spezifikation Verhalten
(„Alben-Grid mit Cover, Titel, Interpret“) und keine konkreten Gestaltungen.
