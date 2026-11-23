# Feature-Gap-Analyse

Quelle: öffentliche Doku (docs.symfonium.app/wiki/settings/settings-all-settings-tree/ und Unterseiten).
Kein Code und keine Assets übernommen — nur dokumentierte Konzepte.
Stand: 2026-11-16, Abgleich gegen K56-Quellcode (SettingsStore/Theme/Screens).

Status: ✅ vorhanden · 🟡 teilweise · ❌ fehlt · ⛔ bewusst nicht
Aufwand: S (< 1 h) · M (1–3 h) · L (> 3 h)

## Interface

| Option | Status | Aufwand | Notiz |
|---|---|---|---|
| Theme (hell/dunkel/System/Matrix) | ✅ | – | ThemeController |
| Primärfarbe / eigene Themes | ❌ | M | Farbpaletten pro Theme |
| Custom Theme Import/Export (JSON) | ❌ | M | Theme-Editor + Online-Katalog |
| Farbpalette-Stil (TonalSpot/Neutral/Vibrant/…) | ❌ | M | Material-3-Scheme-Generierung |
| Cover-Art-Dynamic-Color | ❌ | M | Farbe aus aktuellem Cover ableiten |
| Typografie | ❌ | S | Schriftgröße/Font wählbar |
| Dynamische Farben (Material You) | ❌ | S | Android 12+ |
| Navigationseinträge / Labels | ⛔ | – | 4 feste Tabs |
| Navigation-Layout (Portrait/Landscape) | ❌ | M | Floating-Toolbar / Side-Menu |
| Skip Genre-Detailseite | ❌ | S | direkt zu Alben/Artists |
| Skip Artist-Detailseite | ❌ | S | direkt zu Alben/Tracks |
| Default Song Action | ❌ | S | Play/Enqueue wählbar |
| Show Playback Messages | ❌ | S | kurze Toast-Infos bei Queue-Aktionen |
| Queue next als Drop-Target | ❌ | S | Drag & Drop |
| Expanded Player automatisch öffnen | ❌ | S | bei Wiedergabestart |
| Keep Screen On | ✅ | – | Einstellungen → Darstellung |
| Screen Orientation | ✅ | S | Portrait/Landscape/Auto |
| Rounded Corners (global) | ❌ | S | Eckradius |
| Change Language | ❌ | S | Android-13-App-Sprache |
| Application Logo | ⛔ | S | eigenes Branding (BRANDING.md) |
| Hide Filter Button | ❌ | S | Top-Button-Bar |
| Hide Cast Button | ⛔ | S | kein Cast implementiert |
| Changelog nach Update | ❌ | S | |
| Media Lists: Anzahl anzeigen | ✅ | S | Badge in Listen |
| Media Lists: Zusatzinfo 1/2 | ✅ | S | Album/Interpret-Zeile |
| Media Lists: Top-Button-Row ausblenden | ❌ | S | Quick-Action-Leiste |
| Media Lists: Swipe-Aktionen (links/rechts) | ❌ | M | Play/Queue/Favorit |
| Media Lists: Favorit/Offline-Overlay | ❌ | S | Badge auf Artwork |
| Home: Sektionen + Reihenfolge | ✅ | – | Stift-Dialog |
| Home: Sektionen einzeln konfigurierbar | 🟡 | S | aktuell 4 feste Sektionen |
| Home: Icon wählbar | ❌ | S | oberes Home-Icon |
| Home: Playlist als Row („Add to home") | ❌ | M | |
| Library: Spalten | ✅ | – | Auto/2/3/4 |
| Library: Spalten (Landscape) | ❌ | S | getrennt von Portrait |
| Library: Abstand | ✅ | S | Grid-Spacing 4–16 dp |
| Library: Shortcuts wählbar | ❌ | S | Navigationseinträge |
| Album-Seite: „More from artist" | ✅ | – | weitere Alben des Interpreten als Row |
| Album-Seite: „More from artist" Sort | ❌ | S | |
| Album-Seite: „About" | ✅ | – | Overview + Genre-Chips |
| Album-Seite: About-Zeilen konfigurierbar | ❌ | S | |
| Album-Seite: Künstler immer im Track | ❌ | S | |
| Album-Seite: Header-Style (Live-Preview) | ❌ | M | Größe/Hintergrund/Blur |
| Artist-Seite: „Top tracks"/„Appears on" | ✅ | M | Top-Tracks (PlayCount), Erscheint-auf |
| Artist-Seite: Header-Style (Live-Preview) | ❌ | M | |
| Artist-Seite: Albums Sort/Style/Spalten | ❌ | M | Liste/Grid |
| Artist-Seite: Top-tracks Anzahl + Quelle | ❌ | S | Favoriten/Most-played |
| Artist-Seite: Albums nach Typ gruppieren | 🟡 | S | eigene Alben vs. Erscheint-auf |
| Genre-Seite (komplett) | ✅ | L | Library-Tab mit Genres + Alben/Titel pro Genre |
| Playlist-Seite: Header-Style | ❌ | M | Live-Preview |
| Now Playing: Tracknummer | ✅ | – | Einstellungen → Darstellung |
| Now Playing: Tracknummer in Media Session | ❌ | S | |
| Now Playing: Styles speichern/importieren | ❌ | L | styles.symfonium.app |
| Now Playing: Kompakt/Floating-Player-Style | ❌ | M | Größe/Radius/Schatten |
| Now Playing: runder Progress-Indikator | ❌ | S | |
| Now Playing: Expanded Screen-Style (Portrait/Landscape) | 🟡 | L | zwei Stile (Standard/großes Cover); volles Portrait/Landscape-Layout offen |
| Now Playing: Cover/Background/Button-Styles | 🟡 | M | Cover-Rand 0–24 dp, Hintergrund Gradient/einfarbig/Album-Farbe |
| Now Playing: animierter Progressbar + Overlay-Icons | 🟡 | S | animierte Progressbar (Option); Overlay-Icons offen |
| Now Playing: zusätzliche Künstler | ❌ | S | |
| Now Playing: Thumbnail in Landscape tauschen | ❌ | S | |
| Now Playing: Favorit verstecken | ✅ | S | Option |
| Now Playing: Cover nicht croppen | ✅ | S | ContentScale.Fit (Option) |
| Now Playing: kompakt / Buttons wählbar | ✅ | S | kompakte Bedienelemente + Shuffle/Repeat/Queue/DSP/Sleep/Favorit frei wählbar |
| Now Playing: Rating/Volume/Infos | ❌ | M | |
| Queue unten statt oben | ✅ | – | Button unter Transport |

## Playback

| Option | Status | Aufwand | Notiz |
|---|---|---|---|
| DSP / EQ / Convolver / DDC | ✅ | – | eigener DSP |
| Endlos-Autoplay richtungsgebunden | ✅ | – | Jellyfin InstantMix |
| Resume-Punkt (Position merken) | ✅ | M | SharedPreferences, pro Item |
| Min. Spielzeit für Resume-Punkt (s) | ✅ | S | Schwelle als Einstellung (Default 15 s) |
| Audiobook-Resume-Rollback | ❌ | S | beim Fortsetzen zurückrollen |
| „Save Now Playing state" | ✅ | M | Queue + letzter Titel, fehlende Titel überspringen |
| Multiple queues | ❌ | L | |
| Personal mixes size | 🟡 | S | 60 Titel fix |
| Min. play % bevor „gespielt" | ❌ | S | |
| Circular queue navigation | ✅ | S | Ende → Anfang (Option) |
| Previous/Next-Rewind-Verhalten | 🟡 | S | Prev bei Titelbeginn zurückspulen |
| Retain player state (Shuffle/Repeat/Speed/Pitch) | 🟡 | S | nur Standard |
| Pitch control | ❌ | M | |
| Remember last renderer | ❌ | M | |
| Volume auto play/pause | ❌ | M | bei Lautstärke 0 pausieren |
| Headphone/Bluetooth auto play | ❌ | M | |
| Pause on task removal | ✅ | – | |
| Ignore remote stop command | ❌ | S | Stop als Pause |
| Cellular/WLAN Max-Bitrate | 🟡 | M | Datensparmodus: niedrigere Bitrate + kleiner Buffer bei Mobil |
| Prefer internal decoder | ⛔ | S | ExoPlayer default |
| Offload-Mode | ❌ | M | USB/AAudio |
| Hi-Res local renderer | ❌ | M | |
| Transcoding-Engine | ❌ | L | ffmpeg-Eigenbau |
| ReplayGain | ❌ | M | braucht Tags |
| Enhanced ReplayGain-Processor | ❌ | M | |
| Audio-Focus: Short Action | 🟡 | M | Standardverhalten |
| Audio-Focus: Permanent Loss | 🟡 | M | |
| Headset-Buttons (1/2/3 Klick) | ❌ | M | |
| Headset: Remap Next/Prev | ❌ | S | |
| Headset: Slower click detection | ❌ | S | |
| Notification-Buttons wählbar | ❌ | S | |
| Notification: Skip prev/next zeigen | 🟡 | S | fixe Media-Session-Buttons |
| Notification: Action 1/2/3 | ❌ | M | |
| Bluetooth Lyrics | ❌ | M | |
| Expert EQ-Mode | ❌ | M | |
| EQ Block Size | ❌ | S | |
| Reset AutoEQ-DB | ❌ | S | |
| Media-Session-Queue publizieren | 🟡 | S | |
| USB-Audio-Handling | ⛔ | M | niche |
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
| WLAN-Status nur primäre Verbindung | ❌ | S | VPN/WireGuard-Erkennung |
| Automatisches Offline-Cachen von Favoriten | ✅ | M | respektiert WLAN-only |
| Favoriten in Original-Qualität cachen | ❌ | S | |
| Automatischer Offline-Modus | ❌ | M | |
| Bei Transcode vom Server streamen | ❌ | S | |
| Bei WLAN statt Offline streamen | ❌ | S | |
| Parallele Downloads (Anzahl) | ✅ | S | 1–5 in Einstellungen, Standard 3 |
| Persistente Download-Queue | ✅ | M | JSON-Index `files/offline/queue.json`, atomar + gedrosselt; Zustände queued/active/done/failed; `.part` wird beim Start verworfen und neu geladen; Netz-Gate über NetworkTransport (Mobil pausiert, WLAN setzt fort); Queue-Liste in Einstellungen → Offline |
| HTTP/1.1 für Downloads erzwingen | ❌ | S | |
| Pre-Cache Queue (Mobil/WLAN) | 🟡 | M | rolling cache |
| Ersten Track vor Wiedergabe fertig cachen | ❌ | S | |
| Vollständig gecachte in Rolling-Cache übernehmen | ❌ | M | |
| Bild-Cache / High-Quality-Images | 🟡 | S | Coil-Standard |
| WLAN-only Bild-Downloads | ❌ | S | |
| High-Quality (lossless) Bilder | 🟡 | S | |
| Langsamere Downloads | ⛔ | – | unnötig |

## Database / Advanced

| Option | Status | Aufwand | Notiz |
|---|---|---|---|
| Artikel beim Sortieren ignorieren | ✅ | – | |
| ASCII-Sort | 🟡 | S | nur lowercase |
| Auto-Sync / leere Alben entfernen | ⛔ | – | Server-Aufgabe |
| Waveform-Analyse (lokal/WLAN/jede Verb.) | ❌ | L | lokale/Server-Dateien |
| Album-/Composer in Playcounts | ❌ | S | |
| Album-Playcount-Berechnung | ❌ | S | avg/total/lowest |
| Halbe Sterne (Ratings) | ❌ | S | |
| Metadaten-Scrape-Sprache | ❌ | S | |
| Playlist-Track nur per Pfad matchen | ❌ | S | |
| FastScroll mehr Daten laden | ❌ | S | |
| Settings-PIN | ❌ | S | |
| Debug-Mode | ❌ | S | |
| Debug/Logs teilen | ❌ | M | |
| Manage generated files | ❌ | S | |
| Don't kill my app | ❌ | S | Hilfe-Link |
| Clear image cache | ❌ | S | |
| Clear media info cache | ❌ | S | |
| Cleanup internal states | ❌ | S | |
| DB kompaktieren | ❌ | S | |
| Playback-Historie löschen | ❌ | S | |
| Anonymous Analytics | ⛔ | S | Privacy |
| Anonymous Crash Reporting | ⛔ | S | |
| Disable media session usage | ❌ | S | |
| Metered WLAN = Mobil | ❌ | S | |
| VPN = Mobil | ❌ | S | |
| Backup & Restore der Einstellungen | ✅ | M | JSON-Export/-Import, optional AES-256-GCM-verschlüsselt |

## Android Auto

| Option | Status | Aufwand | Notiz |
|---|---|---|---|
| Android Auto (komplett: Tabs, Home/Library/Favorites-Rows, Display-Styles) | ⛔ | L | kein AA-Support, bewusst out of scope |

## Nächste Schritte (Reihenfolge)

Erledigt (2026-11-16, 11 Commits):
1. ✅ Resume-Punkt + Schwelle (Default 15 s).
2. ✅ Now-Playing: Favorit verstecken, Cover nicht croppen, kompakte Bedienelemente.
3. ✅ Automatisches Offline-Cachen von Favoriten (respektiert WLAN-only).
4. ✅ Circular queue navigation.
5. ✅ Media-Listen: Zusatzinfo + Anzahl.
6. ✅ „Save Now Playing state" (Queue + letzter Titel, fehlende Titel überspringen).
7. ✅ Orientation (Auto/Portrait/Landscape) + Grid-Abstand 4–16 dp.
8. ✅ Backup & Restore der Einstellungen (JSON, optional AES-256-GCM-verschlüsselt).
9. ✅ Queue-Bearbeitung (verschieben/entfernen/leeren).
10. ✅ Player-Buttons frei wählbar.
11. ✅ Expanded Now Playing (Stile, Cover-Rand, Hintergrund, animierte Progressbar).
12. ✅ Cover-Rand (echter Außenabstand, 0–24 dp) getrennt vom Eckradius (0–28 dp).

Erledigt (2026-11-16 abends, Dauerauftrag Batch 2):
13. ✅ Album-Seite: „More from artist", „About" (Overview + Genres).
14. ✅ Artist-Seite: Top-Tracks (PlayCount), „Appears on", Biografie.
15. ✅ Suchseite: lokaler Verlauf (max 10), Filter-Chips, Tastatur-Submit.
16. ✅ Genre-Seite: Library-Tab, Alben/Titel pro Genre.
17. ✅ Playlists: anzeigen/erstellen/umbenennen/löschen/Titel hinzufügen+entfernen (Jellyfin-API), Offline-Hinweis.
18. ✅ Datensparmodus (Frugal): niedrigere Bitrate + kleiner Buffer bei Mobil.
19. ✅ Kontextmenüs: Play-Next/Queue/Shuffle auf Album-, Artist- und Suchseite; Track-Menüs konsistent.

Weiter offen:
- Volles Portrait/Landscape-Layout + freie Button-Styles (Expanded Now Playing abschließen).
- Overlay-Icons auf der animierten Progressbar.
- Playlist-Seite: Header-Style (Live-Preview).
- Album/Artist-Header-Style (Live-Preview, Blur).
- Suchseite: Default Song Action (Play/Enqueue wählbar).
