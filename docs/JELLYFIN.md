# Jellyfin compatibility

**Hard minimum: Jellyfin 10.10.7.**

## Version check

On every sign-in (password or Quick Connect):

1. `GET /System/Info/Public` (no token)
2. compare `Version` component-wise numerically against `10.10.7`
   (`10.10.10 > 10.10.7`, not lexicographic)
3. older servers: abort with a clear message

The comparison lives in `JellyfinVersion` and is unit-tested.

Emby exposes the same API but is not subject to the Jellyfin version check.

## Authentication

Header on every API call:

```
Authorization: MediaBrowser Client="K56", Device="Android",
  DeviceId="<install-uuid>", Version="0.1.0"
X-Emby-Authorization: <same value>
X-Emby-Token: <access-token>
```

- `POST /Users/AuthenticateByName` with `{"Username": "...", "Pw": "..."}`
- `POST /Users/AuthenticateWithQuickConnect` with `{"Secret": "..."}`
- `GET /QuickConnect/Enabled`, `POST /QuickConnect/Initiate`,
  `GET /QuickConnect/Connect?secret=...` (polling every 2 s, max 3 min)

## Library

| Purpose | Endpoint |
|---|---|
| Music libraries | `GET /UserViews?userId={userId}` (filter `CollectionType == "music"`) |
| Albums | `GET /Items?IncludeItemTypes=MusicAlbum&Recursive=true` |
| Artists | `GET /Artists?Recursive=true&SortBy=SortName` |
| Album tracks | `GET /Items?ParentId={albumId}&IncludeItemTypes=Audio` |
| Search | `GET /Items?SearchTerm=…&IncludeItemTypes=MusicAlbum,MusicArtist,Audio` |
| Favourite | `POST/DELETE /UserFavoriteItems/{itemId}?userId={userId}` |

Images: `GET /Items/{id}/Images/Primary?maxHeight=700&maxWidth=700&tag=…&api_key=…`

Why these two routes: `Users/{userId}/Views` and
`Users/{userId}/FavoriteItems/{itemId}` still exist in 10.10.7 but are marked
obsolete there and have since been removed. `/UserViews` and
`/UserFavoriteItems/{itemId}` exist in 10.10.7 **and** in current releases, so
one code path covers the whole supported range.

## Playback

**Direct play** (default for compatible containers):

```
GET /Audio/{itemId}/stream?static=true&MediaSourceId={itemId}
    &DeviceId={deviceId}&api_key={token}
```

**Transcoding** (setting or unknown container):

```
GET /Audio/{itemId}/universal?UserId=…&DeviceId=…
    &MaxStreamingBitrate=320000
    &Container=opus,mp3,aac,m4a,m4b,flac,ogg,wav
    &TranscodingContainer=ts&TranscodingProtocol=hls&AudioCodec=aac
    &api_key=…&EnableRedirection=true
```

The HLS URL is handed to ExoPlayer with `application/x-mpegURL`.

## Playback reporting

| Event | Endpoint | Fields |
|---|---|---|
| Start | `POST /Sessions/Playing` | `ItemId`, `MediaSourceId`, `PlaySessionId`, `PositionTicks`, `PlayMethod=DirectPlay` |
| Progress | `POST /Sessions/Playing/Progress` | plus `IsPaused`, `RepeatMode` (every 10 s) |
| Stop | `POST /Sessions/Playing/Stopped` | `PositionTicks` |

`PositionTicks = milliseconds × 10,000`.

## LAN/Internet

Jellyfin allows one LAN and one Internet address per session.
`EndpointResolver` picks one based on the transport type:

- Wi-Fi/Ethernet → LAN if reachable, otherwise Internet
- mobile data → Internet
- “Prefer external address” → always Internet

LAN discovery: UDP broadcast to port 7359 with `who is JellyfinServer?`.
The answer is JSON with `Address`, `Id`, `Name`.

## Tested server versions

- Target/minimum: **10.10.7**
- Newer 10.10.x / 10.11.x are accepted

Not supported: servers that require endpoints removed before 10.10.

## Verification notes

The endpoint inventory above was checked against the Jellyfin API sources at
tag `v10.10.7` (`Jellyfin.Api/Controllers/{UserViews,UserLibrary,Items,Audio}Controller.cs`)
and against the current OpenAPI description. The client has not yet been run
against a live server in this repository; see the roadmap.
