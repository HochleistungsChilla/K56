#!/usr/bin/env python3
"""Minimal Jellyfin API mock for K56 testing. Stdlib only, no deps.

Serves just enough of the Jellyfin REST surface (10.10.x) to let K56 sign in
and exercise search. Every request is logged to stdout.
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8096

def item(iid, name, itype, **kw):
    d = {"Name": name, "Id": iid, "Type": itype}
    d.update(kw)
    return d

# A realistic-but-tiny library.
def search_items(term):
    t = term.lower()
    if "dup" in t:
        # Duplicate album ids -> potential LazyColumn key collision.
        return [
            item("album-1", "Dup Album A", "MusicAlbum", AlbumArtist="X",
                 ImageTags={"Primary": "a1"}, UserData={"IsFavorite": False}),
            item("album-1", "Dup Album B", "MusicAlbum", AlbumArtist="Y",
                 ImageTags={"Primary": "a1"}, UserData={"IsFavorite": False}),
        ]
    albums = [
        item("album-1", "Test Album", "MusicAlbum",
             AlbumArtist="Test Artist",
             AlbumArtists=[{"Name": "Test Artist", "Id": "artist-1"}],
             Artists=["Test Artist"], ProductionYear=2020, ChildCount=10,
             ImageTags={"Primary": "a1"}, UserData={"IsFavorite": False, "Played": False}),
    ]
    artists = [
        item("artist-1", "Test Artist", "MusicArtist",
             ImageTags={"Primary": "ar1"}, UserData={"IsFavorite": False}),
    ]
    tracks = [
        item("track-1", "Test Track", "Audio",
             Album="Test Album", AlbumId="album-1", Artists=["Test Artist"],
             RunTimeTicks=2000000000, IndexNumber=1, ParentIndexNumber=1,
             MediaSources=[{"Id": "track-1", "Container": "mp3", "Bitrate": 320000}],
             ImageTags={"Primary": "t1"}, UserData={"IsFavorite": False, "Played": False}),
    ]
    return [x for x in albums + artists + tracks if t in json.dumps(x).lower()]

class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _send(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path)
        q = parse_qs(u.query)
        print(f"GET {u.path} {u.query}", flush=True)
        path = u.path.rstrip("/")

        if path == "/System/Info/Public":
            return self._send(200, {
                "ServerName": "k56-mock", "Version": "10.10.7", "Id": "mock-server-id",
                "OperatingSystem": "Linux",
            })
        if path == "/UserViews":
            return self._send(200, {"Items": [], "TotalRecordCount": 0, "StartIndex": 0})
        if path == "/Artists":
            return self._send(200, {"Items": [], "TotalRecordCount": 0, "StartIndex": 0})
        if path == "/Genres":
            return self._send(200, {"Items": [], "TotalRecordCount": 0, "StartIndex": 0})
        if path.startswith("/Users/") and path.endswith("/Items"):
            return self._send(200, {"Items": [], "TotalRecordCount": 0, "StartIndex": 0})
        if path.startswith("/Users/") and path.endswith("/Views"):
            return self._send(200, {"Items": [], "TotalRecordCount": 0, "StartIndex": 0})
        if path == "/Items":
            types = (q.get("IncludeItemTypes") or [""])[0]
            parent = (q.get("ParentId") or [None])[0]
            term = (q.get("searchTerm") or q.get("SearchTerm") or [None])[0]
            if term:
                return self._send(200, {"Items": search_items(term), "TotalRecordCount": len(search_items(term)), "StartIndex": 0})
            if "Playlist" in types:
                pl = [item("playlist-1", "Test Playlist", "Playlist", ChildCount=2)]
                return self._send(200, {"Items": pl, "TotalRecordCount": 1, "StartIndex": 0})
            if parent == "playlist-1":
                tracks = [
                    item("track-1", "Test Track", "Audio",
                         Album="Test Album", AlbumId="album-1", Artists=["Test Artist"],
                         RunTimeTicks=2000000000, IndexNumber=1, ParentIndexNumber=1,
                         MediaSources=[{"Id": "track-1", "Container": "mp3", "Bitrate": 320000}],
                         UserData={"IsFavorite": False, "Played": False}),
                ]
                return self._send(200, {"Items": tracks, "TotalRecordCount": 1, "StartIndex": 0})
            return self._send(200, {"Items": [], "TotalRecordCount": 0, "StartIndex": 0})
        if path.startswith("/Items/"):
            # Item detail: return a generic item.
            iid = path.split("/")[-1]
            return self._send(200, item(iid, "Detail " + iid, "MusicAlbum"))
        # Any other GET -> empty result.
        return self._send(200, {"Items": [], "TotalRecordCount": 0, "StartIndex": 0})

    def do_POST(self):
        u = urlparse(self.path)
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8", "replace")
        print(f"POST {u.path} {body}", flush=True)
        path = u.path.rstrip("/")

        if path == "/Users/AuthenticateByName":
            return self._send(200, {
                "User": {"Id": "mock-user", "Name": "tester"},
                "AccessToken": "mock-token",
                "ServerId": "mock-server-id",
            })
        # Playlists etc.
        return self._send(200, {"Id": "mock-id"})

    def do_DELETE(self):
        u = urlparse(self.path)
        print(f"DELETE {u.path}", flush=True)
        self._send(204, {})

srv = ThreadingHTTPServer(("127.0.0.1", PORT), H)
print(f"mock jellyfin on 127.0.0.1:{PORT}", flush=True)
srv.serve_forever()
