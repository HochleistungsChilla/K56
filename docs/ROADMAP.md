# Roadmap

Stand: 2026-11-22. Grundlage ist `docs/SYMFONIUM-GAP.md` (Funktionsinventar)
plus die neue Anforderung **Acquisition** (Lidarr & andere Downloader).

„Parity" heißt funktional vergleichbar, nicht optisch gleich. Reihenfolge ist
nach Nutzen pro Aufwand sortiert, nicht nach Feature-Liste.

---

## 1. Wo K56 heute steht

### Quellen

| Quelle | Status |
|---|---|
| Jellyfin / Emby | ✅ |
| Subsonic / OpenSubsonic / Navidrome | ✅ |
| Plex | ✅ |
| Audiobookshelf | ✅ |
| Kodi | ✅ |
| Lokale Dateien (MediaStore) | ✅ |
| WebDAV | ✅ (Ordner-Scan, ohne Tags/Cover) |
| SMB v2/v3 | ⬜ |
| Cloud (Drive/OneDrive/Dropbox/Box/pCloud) | ⬜ (OAuth-Apps nötig) |

### Kern

| Bereich | Status | Anmerkung |
|---|---|---|
| Library, Suche, Sortierung | ✅ | Router delegiert vollständig (Fix `3a56318`) |
| Wiedergabe (Media3) | ✅ | Queue, Shuffle, Repeat, Resume |
| Now Playing (kompakt/erweitert) | ✅ | Stil, Cover-Radius, Hintergrund |
| Playlists (CRUD) | ✅ | inkl. Detail-Screen |
| Favoriten (Titel/Album/Interpret) | ✅ | lokal + serverseitig |
| Offline-Downloads | ✅ | persistente Queue, Netz-Gate, `.part`-Recovery |
| Bibliotheks-Sync (inkrementell) | ✅ | voll/incremental, Debug-Log |
| Backup (verschlüsselt) | ✅ | AES-256-GCM, JSON |
| DSP | 🟡 | EQ, Preamp, Limiter, Convolution (DDC/IR) — kein AutoEQ/PEQ |
| Themes / Startseite | ✅ | inkl. konfigurierbarer Begrüßung |

### Was fehlt (Auswahl)

Cast (Chromecast/UPnP/Sonos), Lyrics, ReplayGain, AutoEQ/parametrischer EQ,
Smart Playlists, Android Auto, Widgets, Wear OS, SMB — und **Acquisition**.

---

## 2. Acquisition — Lidarr & Downloader

### Ziel

Musik, die **nicht** in der Bibliothek liegt, aus der App heraus anfordern und
den Fortschritt sehen — ohne den Player zum Medienserver umzubauen.

### Architektur (Vorschlag)

```
data/acquisition/
  AcquisitionService.kt      // Schnittstelle: suchen, anfragen, wanted, queue, history
  lidarr/LidarrApi.kt        // REST /api/v1, Header X-Api-Key
  lidarr/LidarrService.kt    // Matching + Request-Logik
  clients/DownloadClient.kt  // addTorrent/addNzb/addUrl, status, pause/resume
  clients/Qbittorrent.kt     // Web API v2
  clients/Sabnzbd.kt         // API (mode=addurl/addfile, queue)
  clients/Nzbget.kt          // JSON-RPC
  clients/Transmission.kt    // RPC
  clients/Slskd.kt           // Soulseek (HTTP API)
```

**Trennung, die wichtig ist:**

- **Lidarr** ist der *Manager*: es sucht in seinen Indexern, lädt über seine
  eigenen Download-Clients und verschiebt/benennt die Dateien. K56 muss dafür
  **keine** Indexer oder Clients kennen — nur `command` + `queue` + `history`.
- **Direkte Download-Clients** sind die Ergänzung: einen Link/NZB/Torrent an
  einen Client übergeben und den Fortschritt anzeigen. **Keine** Indexer-Suche
  in der App, keine Streaming-Dienst-Extraktion (DRM/ToS).

Wiedergabe bleibt immer beim Medienserver (Jellyfin/Subsonic/…); Acquisition
füllt nur dessen Bibliothek.

### Matching-Problem (der eigentliche Aufwand)

Ein lokales Album muss dem richtigen Lidarr-Album zugeordnet werden. Jellyfin
liefert keine verlässliche MusicBrainz-ID. Deshalb:

1. `GET /api/v1/album/lookup?term=<artist> <album>`
2. Kandidaten anzeigen (Cover, Jahr, Trackzahl) → **Bestätigung durch den Nutzer**
3. Erst dann `POST /api/v1/album` (monitored) + `POST /api/v1/command`
   (`AlbumSearch`/`ArtistSearch`)

Kein stilles Auto-Matching. Ein falsch angefordertes Album ist teurer als ein
Klick.

### Phasen

| # | Paket | Inhalt | Aufwand | Hängt an |
|---|---|---|---|---|
| A0 | Verbindung | Einstellungen → „Acquisition": URL, API-Key, Test-Button, Qualitäts-/Metadatenprofil, Root-Ordner | S | – |
| A1 | Anfrage aus der App | Album-/Interpret-Seite: „Fehlt → anfragen", Lookup-Dialog mit Bestätigung, Command + Feedback | M | A0 |
| A2 | Requests-Screen | Wanted/Missing, Queue, History; Status je Eintrag; Pull-to-refresh | M | A1 |
| A3 | Fertig → Bibliothek | Webhook (Lidarr Connect) oder Polling → Bibliotheks-Sync + Hinweis | S | A2 |
| B1 | Client-Schnittstelle | `DownloadClient` + qBittorrent (Login, add, Liste, pause/resume) | M | – |
| B2 | Usenet | SABnzbd, NZBGet | M | B1 |
| B3 | Weitere | Transmission RPC, slskd | M | B1 |
| B4 | Übergabe | Link/NZB/Torrent aus der App an einen Client; Fortschritt in der Queue-Leiste | S | B1 |
| C1 | Lücken-Liste | Lokale Bibliothek gegen Lidarr-Wanted: „Was fehlt mir?" | M | A2 |
| C2 | Automatik | Periodischer Abgleich + Benachrichtigung bei neuen Alben | S | C1 |

**Reihenfolge:** A0 → A1 → A2 → A3, dann B1 → B2 → B4, dann C1/C2, dann B3.
A0–A2 sind der sichtbare Nutzen; B* ist Fleißarbeit mit vielen Sonderfällen
(API-Versionen, Auth-Modelle, Fehlercodes).

### Risiken

| Risiko | Umgang |
|---|---|
| Falsches Matching | Bestätigungsdialog, Suchbegriff editierbar |
| API-Drift (Lidarr v1 → v3?) | Version beim Test abfragen, Capability-Cache, klare Fehlermeldung |
| API-Keys im Log | Keys nie loggen, `SyncLog` redigieren |
| Cleartext-HTTP im LAN | erlaubt, aber Warnung in den Einstellungen |
| Rechtliches | nur eigene Server/Clients; keine Extraktion aus Streaming-Diensten |

### Offene Entscheidungen

1. **Lidarr zuerst, Clients später?** (empfohlen: ja — Lidarr deckt den
   Normalfall ab, Clients sind Ergänzung)
2. **Welche Clients sind real im Einsatz?** (qBittorrent? SABnzbd? slskd?)
3. **Auto-Request erlaubt?** (z. B. „fehlendes Album eines Favoriten
   automatisch anfragen") — empfohlen: nein, nur manuell.

---

## 3. Parity-Backlog (nach Nutzen/Aufwand)

| Priorität | Thema | Warum | Aufwand |
|---|---|---|---|
| 1 | **DB-Indizes** (`artists(name)`, `artist_favorites`, `tracks(artist_name)`) | Favoriten-Abfrage braucht auf 62k Tracks Sekunden | S |
| 2 | Suche: Paging + Server-Limit | große Bibliotheken liefern sonst nur die ersten Treffer | S |
| 3 | SMB v2/v3 | letzte fehlende Dateiquelle ohne Cloud | L |
| 4 | Smart Playlists (Regeln auf lokaler DB) | starkes Feature, Daten sind schon da | M |
| 5 | Lyrics (LRC + Server) | häufig genutzt, überschaubar | M |
| 6 | ReplayGain (EBU/RG2) | „richtige" Lautstärke, DSP-Kette existiert | S |
| 7 | AutoEQ + parametrischer EQ | Ergänzung zum vorhandenen EQ | M |
| 8 | Android Auto | eigenes Modul, DPAD-Layouts | M |
| 9 | Widgets (Glance) | 4 Typen, Mediensteuerung | M |
| 10 | Cast: Chromecast/UPnP | groß, eigener Lebenszyklus | L |
| 11 | Wear OS | Modul + Offline-Sync | L |
| 12 | Cloud-Quellen (OAuth) | Aufwand pro Anbieter, geringer Nutzen | L |

---

## 4. Nicht-Ziele

- Kein Kopieren von Symfonium-Design, Assets oder Marke
- Keine Umgehung von Bezahl-/Lizenzlogik
- Keine Telemetrie, kein Cloud-Mittelsmann
- Keine Indexer-Suche oder DRM-Extraktion in der App
