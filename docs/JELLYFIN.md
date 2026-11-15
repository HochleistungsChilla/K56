# Jellyfin-Kompatibilität

**Harte Untergrenze: Jellyfin 10.10.7.**

## Versionsprüfung

Bei jedem Login (Passwort oder Quick Connect):

1. `GET /System/Info/Public` (ohne Token)
2. `Version` komponentenweise numerisch mit `10.10.7` vergleichen
   (`10.10.10 > 10.10.7`, nicht lexikografisch)
3. Bei älteren Servern: Abbruch mit klarer Meldung

Der Vergleich liegt in `JellyfinVersion` und ist unit-getestet.

Emby nutzt dieselbe API, unterliegt aber nicht der Jellyfin-Versionsprüfung.

## Authentifizierung

Header an allen API-Aufrufen:

```
Authorization: MediaBrowser Client="Nadelton", Device="Android",
  DeviceId="<install-uuid>", Version="0.1.0"
X-Emby-Authorization: <gleicher Wert>
X-Emby-Token: <access-token>
```

- `POST /Users/AuthenticateByName` mit `{"Username": "...", "Pw": "..."}`
- `POST /Users/AuthenticateWithQuickConnect` mit `{"Secret": "..."}`
- `GET /QuickConnect/Enabled`, `POST /QuickConnect/Initiate`,
  `GET /QuickConnect/Connect?secret=...` (Polling 2 s, max. 3 min)

## Bibliothek

| Zweck | Endpoint |
|---|---|
| Musikbibliotheken | `GET /Users/{userId}/Views` (Filter `CollectionType == "music"`) |
| Alben | `GET /Items?IncludeItemTypes=MusicAlbum&Recursive=true` |
| Interpreten | `GET /Artists?Recursive=true&SortBy=SortName` |
| Album-Tracks | `GET /Items?ParentId={albumId}&IncludeItemTypes=Audio` |
| Suche | `GET /Items?SearchTerm=…&IncludeItemTypes=MusicAlbum,MusicArtist,Audio` |
| Favorit | `POST/DELETE /Users/{userId}/FavoriteItems/{itemId}` |

Bilder: `GET /Items/{id}/Images/Primary?maxHeight=700&maxWidth=700&tag=…&api_key=…`

## Wiedergabe

**Direct Play** (Default für kompatible Container):

```
GET /Audio/{itemId}/stream?static=true&MediaSourceId={itemId}
    &DeviceId={deviceId}&api_key={token}
```

**Transcoding** (Setting oder unbekannter Container):

```
GET /Audio/{itemId}/universal?UserId=…&DeviceId=…
    &MaxStreamingBitrate=320000
    &Container=opus,mp3,aac,m4a,m4b,flac,ogg,wav
    &TranscodingContainer=ts&TranscodingProtocol=hls&AudioCodec=aac
    &api_key=…&EnableRedirection=true
```

Die HLS-URL wird ExoPlayer mit `application/x-mpegURL` übergeben.

## Playback-Reporting

| Ereignis | Endpoint | Felder |
|---|---|---|
| Start | `POST /Sessions/Playing` | `ItemId`, `MediaSourceId`, `PlaySessionId`, `PositionTicks`, `PlayMethod=DirectPlay` |
| Fortschritt | `POST /Sessions/Playing/Progress` | zusätzlich `IsPaused`, `RepeatMode` (alle 10 s) |
| Stop | `POST /Sessions/Playing/Stopped` | `PositionTicks` |

`PositionTicks = Millisekunden × 10 000`.

## LAN/Internet

Jellyfin unterstützt pro Session eine LAN- und eine Internet-Adresse.
`EndpointResolver` wählt anhand des Transporttyps:

- WLAN/Ethernet → LAN, wenn erreichbar, sonst Internet
- Mobilfunk → Internet
- „Externe Adresse bevorzugen“ → immer Internet

LAN-Discovery: UDP-Broadcast an Port 7359 mit `who is JellyfinServer?`.
Antwort: JSON mit `Address`, `Id`, `Name`.

## Getestete Serverstände

- Ziel/Minimum: **10.10.7**
- Neuere 10.10.x / 10.11.x werden akzeptiert

Nicht unterstützt: Server, die vor 10.10 entfernte Endpoints benötigen.
