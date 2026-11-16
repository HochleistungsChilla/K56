# K56

[![Android](https://github.com/HochleistungsChilla/K56/actions/workflows/android.yml/badge.svg)](https://github.com/HochleistungsChilla/K56/actions/workflows/android.yml)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--orlater-blue.svg)](LICENSE)

An open-source Android music player with a multi-backend architecture.

K56 is an **independent implementation**. Its interface and feature set are
inspired by [Symfonium](https://symfonium.app), YouTube Music and
ViPER4Android/JamesDSP. No code, artwork or trademarks were taken from those
projects.

**Status:** 0.1.0 — a working multi-server client with library, search,
background playback and LAN/WAN routing.

**K56** · **Domain:** `k56.wtf` (planned) · **Theme:** Monochrom · **Easter egg:** ATDT  
Tagline: “56k for your ears.”

## Platform

| Requirement | Value |
|---|---|
| Android | **9 (API 28) or newer** |
| Jellyfin | **10.10.7 or newer** (checked at sign-in) |
| Build | JDK 17+ (tested with 21), Android SDK 35 |

## Supported sources

| Source | Status | Sign-in |
|---|---|---|
| **Jellyfin** | ✅ | LAN/Internet address, username/password, Quick Connect, UDP discovery |
| **Emby** | ✅ | LAN/Internet address, username/password |
| **Subsonic** | ✅ | address, username/password (salt+MD5 token) |
| **Navidrome** | ✅ | same as Subsonic |
| **OpenSubsonic** | ✅ | same as Subsonic |
| **Plex** | ✅ | PIN at `plex.tv/link`, server discovery via plex.tv |
| **Audiobookshelf** | ✅ | address, username/password; books → albums, chapters → tracks |
| **Kodi** | ✅ | JSON-RPC, optional HTTP basic auth |
| **Local files** | ✅ | MediaStore permission (Android 9/13+ permissions) |
| **WebDAV** | ✅ | address, optional basic auth; folder scan without tag/cover parsing |
| SMB (v2/v3) | ⬜ planned | file backend |
| Google Drive / OneDrive / Dropbox / Box / pCloud | ⬜ planned | OAuth backend |

All adapters sit behind `MediaRepository`; a new source only touches the data
layer, never the UI.

## Features (0.1.0)

- Library: albums, artists, tracks (grid/list)
- Search across albums, artists and tracks
- Album and artist detail screens
- Playback via **Media3/ExoPlayer** with MediaSession, notification and system
  media controls
- Direct play for common containers; optional transcoding
  (Jellyfin: AAC/HLS, Subsonic: MP3)
- Playback reporting: Jellyfin (position/resume), Plex (timeline),
  Audiobookshelf (progress), Subsonic (scrobble)
- **LAN/Internet routing without any location permission**
- Material 3, dark/light, custom icon

## LAN/Internet routing

Two addresses can be stored per server:

- **LAN** e.g. `http://192.168.1.10:8096`
- **Internet** e.g. `https://music.example.com`

Selection at runtime:

| Network | Behaviour |
|---|---|
| Wi-Fi / Ethernet | LAN address if a short reachability probe succeeds, otherwise Internet |
| Mobile data | always the Internet address |
| offline | last known address (errors are reported cleanly) |

Detection relies solely on `ConnectivityManager` transport types
(Wi-Fi/cellular/Ethernet). **No SSID, no location permission.** “Prefer
external address” can be enabled manually.

Jellyfin servers on the LAN are additionally found via UDP broadcast on port
7359 (`who is JellyfinServer?`).

## Architecture

```
app/src/main/java/app/k56/player/
├── core/model/          domain models + BackendKind
├── core/net/            NetworkMonitor, EndpointResolver, URL normalisation
├── data/                SessionStore, SettingsStore, MediaRepository, router
├── data/jellyfin/       Jellyfin/Emby: API, DTOs, discovery
├── data/subsonic/       Subsonic/OpenSubsonic/Navidrome
├── data/plex/           plex.tv PIN + PMS API
├── data/abs/            Audiobookshelf
├── data/kodi/           Kodi JSON-RPC
├── data/local/          MediaStore
├── playback/            MediaSessionService, ExoPlayer, MediaController
├── di/                  manual app container
└── ui/                  Compose: theme, navigation, screens, components
```

Deliberate choices: no Hilt/Koin, no Room in the MVP, Retrofit +
kotlinx.serialization for Jellyfin, small hand-written clients for the
remaining protocols.

## Build

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # version/routing logic
```

The UI ships in English (`res/values/`) and German (`res/values-de/`). All
user-facing strings live in string resources; there are no hard-coded UI
strings in code.

## Known limits

- Subsonic has no “all tracks” endpoint; the track list uses `getRandomSongs`
  and sorts locally. A local sync index is planned.
- Kodi resolves playback URLs per queue entry via `Files.PrepareDownload`, so
  very large queues start more slowly.
- WebDAV reads metadata from folder/file names (no TagLib, no covers); the scan
  is capped at 300 directories / 5,000 files.
- SMB and cloud drives are not implemented yet (see
  [docs/ROADMAP.md](docs/ROADMAP.md)).
- Tokens are stored in private SharedPreferences; Keystore encryption is
  planned as hardening.

## Legal

K56 is an independent project. Interfaces and protocols are implemented from
public documentation; the interface and feature set are inspired by Symfonium,
YouTube Music and ViPER4Android/JamesDSP. No source code, assets or marks were
copied. The working method is documented in
[docs/CLEANROOM.md](docs/CLEANROOM.md). “Symfonium”, “YouTube Music”,
“ViPER4Android” and “JamesDSP” are trademarks of their respective owners; this
project is not affiliated with any of them.

## Contributing

Pull requests are welcome — rules and build instructions are in
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

**GPL-3.0-or-later** — see [LICENSE](LICENSE).

Forking is explicitly encouraged: clone it, change it, give it your own name,
build your own theme, fork it, send pull requests. The only GPL condition is
that distributed derivative works stay under the GPL and keep their source
open. Forking K56 does not require calling it “K56” — only staying open.
