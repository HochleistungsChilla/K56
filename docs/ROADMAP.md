# Feature parity & roadmap

Reference: publicly documented features of Symfonium (symfonium.app, as of
2026-11-15). “Parity” means functionally comparable, not visually identical.

## Sources

| Source | Status |
|---|---|
| Jellyfin / Emby | ✅ |
| Subsonic / OpenSubsonic / Navidrome | ✅ |
| Plex | ✅ |
| Audiobookshelf | ✅ |
| Kodi | ✅ |
| Local files (MediaStore) | ✅ |
| WebDAV | ✅ (folder scan, no tags/covers) |
| SMB (v2/v3) | ⬜ |
| Google Drive | ⬜ |
| OneDrive | ⬜ |
| Dropbox | ⬜ |
| Box | ⬜ |
| pCloud | ⬜ |

File and cloud sources need their own data sources (SMB streaming, WebDAV
PROPFIND, OAuth apps). The backend interface is prepared for them; the adapters
follow.

## Features

| Area | K56 0.1.0 | Next step |
|---|---|---|
| Library (albums/artists/tracks) | ✅ | genres, years, sorting |
| Search | ✅ | server and local full-text search |
| Playback | ✅ Media3/ExoPlayer | gapless, crossfade |
| Background + notification | ✅ | Android Auto |
| Playback reporting | ✅ Jellyfin/Plex/ABS/Subsonic | scrobble error reporting |
| LAN/Internet routing | ✅ without location | manual server priority |
| Offline cache / downloads | ⬜ | Room + cache directory |
| Playlists | ⬜ | Jellyfin/Subsonic/ABS |
| Smart playlists | ⬜ | rule engine on local DB |
| Lyrics (synchronised) | ⬜ | LRC + server lyrics |
| Equalizer / AutoEQ / PEQ | ⬜ | Media3 AudioProcessor |
| ReplayGain / crossfeed | ⬜ | DSP chain |
| Hi-res / DSD / USB DAC | ⬜ | AudioTrack/USB path |
| Chromecast / UPnP / Sonos | ⬜ | Cast SDK / SSDP |
| Android TV | ⬜ | DPAD navigation |
| Wear OS | ⬜ | module + offline sync |
| Widgets | ⬜ | Glance |
| Themes / now-playing layouts | ⬜ | theme engine |

## Architecture for the next steps

1. **`MediaRepository`** is the seam: every new provider implements it and the
   `RepositoryRouter` selects one based on the session.
2. **Local DB (Room)** for offline metadata, cache index, favourites, playlists
   and smart-playlist rules.
3. **Encapsulate the audio pipeline**: direct play → transcode fallback → DSP.
4. **File backends** (SMB/WebDAV) through custom `DataSource` implementations
   for Media3, so headers and streams behave correctly.
5. **Cloud** with OAuth client IDs that users register themselves.

## Non-goals

- No copying of Symfonium designs, assets or mark
- No circumvention of payment/licence logic
- No telemetry and no cloud middleman
