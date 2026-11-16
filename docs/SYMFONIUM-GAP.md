# Symfonium → K56 Gap-Analyse

Quelle: öffentliche Doku (docs.symfonium.app/wiki/settings/settings-all-settings-tree/ und Unterseiten).
Kein Code, keine APK, kein Decompile — nur dokumentierte Konzepte.

Status: ✅ vorhanden · 🟡 teilweise · ❌ fehlt · ⛔ bewusst nicht
Aufwand: S (< 1 h) · M (1–3 h) · L (> 3 h)

## Interface

| Option | Status | Aufwand | Notiz |
|---|---|---|---|
| Theme (hell/dunkel/System/Matrix) | ✅ | – | ThemeController |
| Primärfarbe / eigene Themes | ❌ | M | Farbpaletten pro Theme |
| Typografie | ❌ | S | Schriftgröße/Font wählbar |
| Dynamische Farben (Material You) | ❌ | S | Android 12+ |
| Navigationseinträge / Labels | ⛔ | – | 4 feste Tabs |
| Keep Screen On | ✅ | – | Einstellungen → Darstellung |
| Screen Orientation | ❌ | S | Portrait/Landscape/Auto |
| Media Lists: Anzahl anzeigen | ❌ | S | Badge in Listen |
| Media Lists: Zusatzinfo 1/2 | 🟡 | S | Album/Interpret-Zeile |
| Home: Sektionen + Reihenfolge | ✅ | – | Stift-Dialog |
| Home: Sektionen einzeln konfigurierbar | 🟡 | S | aktuell 4 feste Sektionen |
| Library: Spalten | ✅ | – | Auto/2/3/4 |
| Library: Abstand | ❌ | S | Grid-Spacing |
| Album-Seite: „More from artist" | ❌ | S | |
| Album-Seite: „About" | ❌ | M | braucht Metadaten |
| Artist-Seite: „Top tracks"/„Appears on" | ❌ | M | |
| Now Playing: Tracknummer | ✅ | – | Einstellungen → Darstellung |
| Now Playing: Favorit verstecken | ❌ | S | |
| Now Playing: Cover nicht croppen | ❌ | S | ContentScale.Fit |
| Now Playing: kompakt / Buttons wählbar | ❌ | M | |
| Now Playing: Rating/Volume/Infos | ❌ | M | |
| Queue unten statt oben | ✅ | – | Button unter Transport |

## Playback

| Option | Status | Aufwand | Notiz |
|---|---|---|---|
| DSP / EQ / Convolver / DDC | ✅ | – | eigener DSP |
| Endlos-Autoplay richtungsgebunden | ✅ | – | Jellyfin InstantMix |
| Resume-Punkt (Position merken) | ❌ | M | Prio 1 |
| „Save Now Playing state" | ❌ | M | letzter Titel + Queue |
| Multiple queues | ❌ | L | |
| Personal mixes size | 🟡 | S | 60 Titel fix |
| Min. play % bevor „gespielt" | ❌ | S | |
| Circular queue navigation | ❌ | S | Ende → Anfang |
| Pitch control | ❌ | M | |
| Headphone/Bluetooth auto play | ❌ | M | |
| Pause on task removal | ✅ | – | |
| Cellular/WLAN Max-Bitrate | 🟡 | M | nur Download-Qualität |
| ReplayGain | ❌ | M | braucht Tags |
| Audio-Focus-Optionen | 🟡 | M | Standardverhalten |
| Headset-Buttons (1/2/3 Klick) | ❌ | M | |
| Notification-Buttons wählbar | ❌ | S | |
| Gapless | ✅ | – | ExoPlayer default |
| Crossfade / Smart Fades | ❌ | L | eigener AudioProcessor |

## Offline / Cache / Download

| Option | Status | Aufwand | Notiz |
|---|---|---|---|
| Speicherort intern/SD | ✅ | – | |
| Download-Qualität (Original/320/192/128, AAC/MP3) | ✅ | – | |
| Streaming-Cache-Größe (aus/…/unbegrenzt) | ✅ | – | |
| Cache-Manager | ✅ | – | |
| Nur WLAN für Downloads | ✅ | – | |
| Automatisches Offline-Cachen von Favoriten | ❌ | M | Prio 2 |
| Automatischer Offline-Modus | ❌ | M | |
| Bild-Cache / High-Quality-Images | 🟡 | S | Coil-Standard |
| Langsamere Downloads | ⛔ | – | unnötig |

## Database / Advanced

| Option | Status | Aufwand | Notiz |
|---|---|---|---|
| Artikel beim Sortieren ignorieren | ✅ | – | |
| ASCII-Sort | 🟡 | S | nur lowercase |
| Auto-Sync / leere Alben entfernen | ⛔ | – | Server-Aufgabe |
| Settings-PIN | ❌ | S | |
| Debug/Logs teilen | ❌ | M | |
| Backup & Restore der Einstellungen | ❌ | M | |

## Nächste Schritte (Reihenfolge)

1. Resume-Punkt + „Save Now Playing" (M) — größter Effekt beim Start.
2. Now-Playing-Optionen: Tracknummer ✅, Favorit verstecken, Cover nicht croppen (S).
3. Automatisches Offline-Cachen von Favoriten (M, respektiert WLAN-only).
4. Circular queue navigation (S).
5. Media-Listen-Zusatzinfo + Anzahl (S).
6. Orientation + Grid-Abstand (S).
