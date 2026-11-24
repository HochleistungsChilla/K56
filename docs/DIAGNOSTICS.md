# Verbose diagnostics

K56 keeps a small in-memory ring of the HTTP requests it makes, shown under
**Settings → Diagnostics**. It is deliberate about privacy: method, path
(without the query string), status, duration and a coarse error class. No
headers, no bodies, no query values.

That is enough to see a failing status, but not enough to answer questions
like "which `SortBy` did the server reject with a `400`?" or "was a media
source even opened before the position stayed at zero?". The verbose mode
answers those.

## Enabling it

**Settings → Library sync → Verbose diagnostics** (debug build only).

- The switch is persisted and survives a restart.
- The release build ignores it: the setting can be stored, but the ring stays
  at its normal size and no extra data is kept.
- Default is off.

## What changes when it is on

| | off | on (debug) |
|---|---|---|
| Ring size | 200 entries | 2000 entries |
| Query values | never stored | stored, credentials redacted |
| Player events | not recorded | one line per transition, play/pause, state change, error, and every 10 s |
| Media requests | recorded since #16 | recorded |

Entries are tagged `API`, `MEDIA` or `PLAYER` in the copied text (`media` in
the list).

### Player entries

```
PLAYER  transition state=BUFFERING src=set
        pos=0ms dur=277000ms buf=0ms id=3b9c05f7…
PLAYER  playing state=READY src=set
        pos=400ms dur=277000ms buf=65536ms id=3b9c05f7…
PLAYER  tick state=READY src=set
        pos=10400ms dur=277000ms buf=131072ms id=3b9c05f7…
```

`src=set` answers "is a source attached at all"; `state=BUFFERING` with
`pos=0ms buf=0ms` is the signature of a stream that never delivers bytes.

### Failed requests

A failed request keeps its parameter **names** in every mode, and with verbose
mode the values as well - except for credentials:

```
GET 400 /Items  IncludeItemTypes=Audio&SortBy=&Limit=500&api_key=***
```

## Privacy

`api_key`, `token`, `access_token`, `x-emby-token`, `pw`, `password`, `secret`
and `apitoken` always lose their value, in both modes. Values are truncated to
120 characters and the whole line to 400, so a buffer cannot become a log
dump. Nothing is written to disk, logcat or a crash report; closing the app
clears it.

## Reproducing a playback problem

1. Enable the switch, start a track, wait for the failure (or the silence).
2. **Settings → Diagnostics**. The newest entry is on top.
3. Copy (top-right icon) and attach the text.

What to look for:

- no `GET /Audio/…/stream` at all → the player never opened a source;
- `/Audio/…` with `TIMEOUT`/`HTTP 4xx` → the request failed, the toast names
  the reason;
- `PLAYER state=BUFFERING pos=0ms buf=0ms` repeated → bytes are not arriving;
- `POST /Sessions/Playing` only after `playing` → the report matches reality.
