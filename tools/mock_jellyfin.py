#!/usr/bin/env python3
"""Minimal Jellyfin API mock for K56 testing. Stdlib only, no deps.

Serves just enough of the Jellyfin 10.10.x REST surface to let K56 sign in and
exercise the whole UI: a small library (artists, albums, tracks, genres,
playlists), search, and a deliberately slow audio stream so in-flight
downloads can be observed. Every request is logged to stdout.

Usage: python3 mock_jellyfin.py [port]
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8096

TICKS = 10_000_000  # .NET ticks per second

TRACK_NAMES = {
    "album-1": ["Test Track", "Zweiter Track", "Dritter Track"],
    "album-2": ["Blaue Stunde", "Nachtfahrt", "Am Kanal"],
    "album-3": ["Keller Sessions", "Beton", "Rauch"],
}

ALBUMS = [
    ("album-1", "Test Album", "artist-1", "Test Artist", 2020, "Rock"),
    ("album-2", "Blaue Stunde", "artist-2", "Nachtzug", 2022, "Electronic"),
    ("album-3", "Keller Sessions", "artist-3", "Kellerkind", 2024, "Electronic"),
]

ARTISTS = [
    ("artist-1", "Test Artist"),
    ("artist-2", "Nachtzug"),
    ("artist-3", "Kellerkind"),
]


def img(tag):
    return {"Primary": tag} if tag else {}


def album_item(aid, name, artist_id, artist_name, year, genre):
    return {
        "Name": name, "Id": aid, "Type": "MusicAlbum",
        "AlbumArtist": artist_name,
        "AlbumArtists": [{"Name": artist_name, "Id": artist_id}],
        "Artists": [artist_name],
        "ProductionYear": year,
        "Genres": [genre],
        "ChildCount": len(TRACK_NAMES[aid]),
        "ImageTags": img("a-" + aid),
        "UserData": {"IsFavorite": False, "Played": False},
    }


def artist_item(aid, name):
    owned = [a for a in ALBUMS if a[2] == aid]
    return {
        "Name": name, "Id": aid, "Type": "MusicArtist",
        "ImageTags": img("ar-" + aid),
        "ChildCount": len(owned),
        "UserData": {"IsFavorite": False, "Played": False},
    }


def tracks_for(album):
    aid, aname, artist_id, artist_name, year, genre = album
    out = []
    for idx, tname in enumerate(TRACK_NAMES[aid], start=1):
        tid = f"{aid}-t{idx}"
        out.append({
            "Name": tname, "Id": tid, "Type": "Audio",
            "Album": aname, "AlbumId": aid,
            "AlbumArtists": [{"Name": artist_name, "Id": artist_id}],
            "Artists": [artist_name],
            "RunTimeTicks": 200 * TICKS,
            "IndexNumber": idx, "ParentIndexNumber": 1,
            "Genres": [genre], "ProductionYear": year,
            "MediaSources": [{"Id": tid, "Container": "mp3", "Bitrate": 320000}],
            "ImageTags": img("t-" + tid),
            "UserData": {"IsFavorite": False, "Played": False},
        })
    return out


def all_tracks():
    out = []
    for a in ALBUMS:
        out += tracks_for(a)
    return out


def all_albums():
    return [album_item(*a) for a in ALBUMS]


def all_artists():
    return [artist_item(*a) for a in ARTISTS]


def playlist_item():
    return {
        "Name": "Test Playlist", "Id": "playlist-1", "Type": "Playlist",
        "ChildCount": 2, "ImageTags": img("pl-1"),
        "UserData": {"IsFavorite": False, "Played": False},
    }


def playlist_tracks():
    keep = {t["Id"] for t in all_tracks()[:2]}
    return [t for t in all_tracks() if t["Id"] in keep]


def search_items(term):
    t = term.lower()
    if "dup" in t:
        # Duplicate album ids -> potential LazyColumn key collision.
        return [
            album_item("album-1", "Dup Album A", "artist-1", "X", 2020, "Rock"),
            album_item("album-1", "Dup Album B", "artist-2", "Y", 2021, "Rock"),
        ]
    pool = all_albums() + all_artists() + all_tracks()
    return [x for x in pool if t in json.dumps(x).lower()]


def by_id(iid):
    for a in all_albums():
        if a["Id"] == iid:
            return a
    for a in all_artists():
        if a["Id"] == iid:
            return a
    for t in all_tracks():
        if t["Id"] == iid:
            return t
    if iid == "playlist-1":
        return playlist_item()
    return None


def page(items):
    return {"Items": items, "TotalRecordCount": len(items), "StartIndex": 0}


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _send(self, code, obj, ctype="application/json"):
        body = json.dumps(obj).encode() if not isinstance(obj, bytes) else obj
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path)
        q = parse_qs(u.query)
        print(f"GET {u.path} {u.query}", flush=True)
        path = u.path.rstrip("/")
        first = lambda k: (q.get(k) or [None])[0]

        if path.startswith("/Audio/"):
            # Slow audio "stream" so in-flight downloads can be observed.
            time.sleep(3)
            body = b"mock-audio-bytes" * 1024
            self._send(200, body, "audio/mpeg")
            return

        if path == "/QuickConnect/Enabled":
            return self._send(200, True)
        if path == "/QuickConnect/Connect":
            return self._send(200, {
                "Secret": first("secret") or "mock-secret",
                "Code": "123456",
                "Authenticated": False,
            })
        if path == "/Users/Me":
            return self._send(200, {"Id": "mock-user", "Name": "tester"})
        if path == "/System/Info/Public":
            return self._send(200, {
                "ServerName": "k56-mock", "Version": "10.10.7", "Id": "mock-server-id",
                "OperatingSystem": "Linux",
            })
        if path == "/Users/Me":
            return self._send(200, {"Id": "mock-user", "Name": "tester"})
        if path == "/UserViews":
            return self._send(200, page([
                {"Name": "Music", "Id": "lib-music", "Type": "CollectionFolder",
                 "CollectionType": "music"},
            ]))
        if path == "/Artists":
            return self._send(200, page(all_artists()))
        if path == "/Genres":
            genres = {}
            for a in ALBUMS:
                genres.setdefault(a[5], []).append(a[0])
            return self._send(200, page([
                {"Name": g, "Id": "genre-" + g.lower(), "Type": "MusicGenre",
                 "ChildCount": len(ids)}
                for g, ids in genres.items()
            ]))
        if path.startswith("/Users/") and path.endswith("/Items"):
            return self._send(200, page(all_tracks()))
        if path.startswith("/Users/") and path.endswith("/Views"):
            return self._send(200, page([
                {"Name": "Music", "Id": "lib-music", "Type": "CollectionFolder",
                 "CollectionType": "music"},
            ]))

        if path == "/Items":
            types = first("IncludeItemTypes") or ""
            parent = first("ParentId")
            term = first("searchTerm") or first("SearchTerm")
            if term:
                items = search_items(term)
                return self._send(200, page(items))
            if "Playlist" in types:
                return self._send(200, page([playlist_item()]))
            if parent == "playlist-1":
                return self._send(200, page(playlist_tracks()))
            if "MusicAlbum" in types:
                if parent and parent.startswith("artist-"):
                    items = [a for a in all_albums()
                             if any(x[2] == parent and x[0] == a["Id"] for x in ALBUMS)]
                    return self._send(200, page(items))
                return self._send(200, page(all_albums()))
            if "MusicArtist" in types:
                return self._send(200, page(all_artists()))
            if "MusicGenre" in types or "Genre" in types:
                genres = {}
                for a in ALBUMS:
                    genres.setdefault(a[5], []).append(a[0])
                return self._send(200, page([
                    {"Name": g, "Id": "genre-" + g.lower(), "Type": "MusicGenre",
                     "ChildCount": len(ids)} for g, ids in genres.items()
                ]))
            if "Audio" in types:
                if parent and parent.startswith("album-"):
                    for a in ALBUMS:
                        if a[0] == parent:
                            return self._send(200, page(tracks_for(a)))
                if parent and parent.startswith("artist-"):
                    tracks = []
                    for a in ALBUMS:
                        if a[2] == parent:
                            tracks += tracks_for(a)
                    return self._send(200, page(tracks))
                if parent and parent.startswith("genre-"):
                    g = parent[len("genre-"):]
                    tracks = []
                    for a in ALBUMS:
                        if a[5].lower() == g:
                            tracks += tracks_for(a)
                    return self._send(200, page(tracks))
                return self._send(200, page(all_tracks()))
            # Fallback: everything, so an unknown listing is not silently empty.
            return self._send(200, page(all_albums() + all_tracks()))

        if path.startswith("/Items/"):
            iid = path.split("/")[-1]
            found = by_id(iid)
            if found:
                return self._send(200, found)
            return self._send(404, {"error": "not found"})

        # Any other GET -> empty result.
        return self._send(200, page([]))

    def do_POST(self):
        u = urlparse(self.path)
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", "replace")
        print(f"POST {u.path} {body}", flush=True)
        path = u.path.rstrip("/")

        if path == "/QuickConnect/Initiate":
            return self._send(200, {
                "Secret": "mock-secret",
                "Code": "123456",
                "Authenticated": False,
            })
        if path == "/Users/AuthenticateByName":
            return self._send(200, {
                "User": {"Id": "mock-user", "Name": "tester"},
                "AccessToken": "mock-token",
                "ServerId": "mock-server-id",
            })
        if path == "/Playlists":
            return self._send(200, {"Id": "playlist-1"})
        return self._send(200, {"Id": "mock-id"})

    def do_DELETE(self):
        u = urlparse(self.path)
        print(f"DELETE {u.path}", flush=True)
        self._send(204, {})


srv = ThreadingHTTPServer(("127.0.0.1", PORT), H)
print(f"mock jellyfin on 127.0.0.1:{PORT}", flush=True)
srv.serve_forever()
