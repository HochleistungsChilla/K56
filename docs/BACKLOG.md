# Backlog

Arbeitsliste für K56: was Fran will, was daraus wurde, was noch offen ist.
Wird laufend nachgeführt — pro Wunsch eine Zeile plus Commit-Hash.

Legende: **offen** · **läuft** · **erledigt** · **verworfen** · **Frage**

---

## Wünsche von Fran

### 2026-11-22

| # | Wunsch | Stand | Wo |
|---|---|---|---|
| 1 | Persistente Download-Queue (übersteht App-Kill + Netzwerkwechsel, `.part`-Recovery, Netz-Gate, 3 Versuche, UI) | **erledigt** | `fb39914`, `e0813ba`, `73df5b0`, `34fa031`, `2985509` |
| 2 | Begrüßung am Start einstellbar (Aus / Hi / Servername / Persönlich) | **erledigt** | `3e50745` |
| 3 | Roadmap statt Symfonium-Paritätsliste | **erledigt** | `7b142e8`, `7dbe58b` |
| 4 | **Lidarr + andere Downloader** unterstützen | **läuft** | A0 `847eb71`/`696c869`, A1 `274ec1e`, A2 `276ad43`, A3 `ca97456` |
| 5 | Backlog mitschreiben (diese Datei) | **erledigt** | `docs/BACKLOG.md` |
| 6 | K56-App im Emulator zeigen | **erledigt** | Fenster-Emulator, echter Server verbunden |
| 7 | `codegraph` nutzen (K56 + symfonik-re, zweiterer nur Inspiration) | **erledigt** | beide Indizes gesynct |

### 2026-11-22 — UI-Pass (UI-Session)

| # | Wunsch | Stand | Wo |
|---|---|---|---|
| 14 | Farbhierarchie: alle Material-3-Rollen gesetzt (kein Baseline-Lila mehr), Nav-Indicator theme-folgend, Typo-Rollen definiert | **erledigt** | `a6a6b1c` |
| 15 | Settings-Karten: ein `SettingsSection` statt 15 Kopien (Abstand/Radius/Padding an einer Stelle) | **erledigt** | `c03773b` |
| 16 | Home-Skeletons statt Spinner beim ersten Laden, Maße = echte Karten (150 dp Alben, 118 dp Künstler) | **erledigt** | `8df6606`, `6de09fb` |
| 17 | Mini-Player beim Verschwinden animieren | **verworfen** | schon vorhanden: `AnimatedVisibility` = fade + expand/shrink, kein Eingriff nötig |

### Ältere Punkte (vor 22.11., Details in `docs/SYMFONIUM-GAP.md` / `docs/ROADMAP.md`)

| # | Wunsch | Stand |
|---|---|---|
| 8 | Downloads überall (Album/Interpret/Suche/Playlist/Genre) | **erledigt** `59bea31` |
| 9 | Home-Sektionen: Weiterhören, Verlauf, Favoriten, Playlists, Genres | **erledigt** `c2a7236`, `63eda9b`, `5a975d3` |
| 10 | Playlist-Detailseite | **erledigt** `dce475c` |
| 11 | Router-Bug: Playlists/Genres/Counts/Delta liefen ins Leere | **erledigt** `3a56318` |
| 12 | Such-Crash bei doppelten IDs | **erledigt** `0c4da09` |
| 13 | Emulator-Testumgebung + Mock-Jellyfin | **erledigt** `23a0093`, `3520f69` |

---

## Offene Entscheidungen (Fragen an Fran)

| # | Frage | Empfehlung | Stand |
|---|---|---|---|
| F1 | Lidarr zuerst, Download-Clients später? | ja | **Frage** |
| F2 | Welche Downloader laufen wirklich (qBittorrent, SABnzbd, NZBGet, Transmission, slskd)? | – | **Frage** |
| F3 | Auto-Request für Favoriten erlauben? | nein, nur manuell | **Frage** |
| F4 | A3 (fertig → Bibliotheks-Sync) oder B1 (qBittorrent) als nächstes? | A3 | **Frage** |
| F5 | A2 als Dialog (aktuell) oder eigener Screen mit Route? | Dialog reicht | **Frage** |

---

## Nächste Schritte (meine Sicht)

1. **B1** — `DownloadClient`-Schnittstelle + qBittorrent (A3 is erledigt: Import-Hinweis + „Bibliothek aktualisieren")
2. **DB-Indizes** (`artists(name)`, `artist_favorites`, `tracks(artist_name)`) — braucht Freigabe für `LibraryDb`
3. Such-Paging für große Bibliotheken
4. A2/A3 mit echtem API-Key gegenprüfen (bisher nur Fehlerpfad 401 verifiziert)

---

## Regeln, die gelten

- **Kein Symfonium-Nachbau.** `SYMFONIUM-GAP.md` und `symfonik-re` sind Inspiration, kein Pflichtenheft.
- **`symfonik-re` nur für Funktionsweise** (Protokolle, Formate) — keine Klassennamen, Layouts, Strings oder Code übernehmen.
- **Kein Push** von mir; local commits only. Die UI-Session pusht ihre Commits.
- **Geteilte Dateien:** `LibraryDb`, `LibrarySync`, `SyncLogScreen`, `PlayerScreen`, `LibraryScreen` nur nach Freigabe. `ui/theme/Theme.kt`, `ui/components/Components.kt`, Nav-Bar-Block in `AppRoot.kt`, `SettingsScreen.kt` gehören der UI-Session.
- **Nix löschen** auf Frans Jellyfin/Lidarr. Nur lesende Calls plus das, was er im Dialog bestätigt.
- **API-Keys** nie über den Chat, nie in Logs/URLs.

---

## Notizen / Sackgassen

- `LibraryDb.favoriteTracks()` ist auf 62k Tracks langsam (sortiert über alle Zeilen, korrelierte `EXISTS`). Läuft jetzt im Hintergrund, braucht aber Indizes.
- ANR-Ursachen waren: `needsFullSync()`/`favoriteTracks()` in der Composition, `recordPlay()` im Media-Listener, `counts()` in Settings. Alle nach `Dispatchers.IO` bzw. in den Service-Scope verschoben.
- Emulator: `10.0.2.2:8096` = Mock, `192.168.0.93:8096` = echter Jellyfin, `192.168.0.93:8686` = Lidarr.
- `uiautomator dump` ist bei Animationen unzuverlässig („null root node") — Screenshot + feste Koordinaten sind oft schneller.
