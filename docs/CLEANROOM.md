# Cleanroom process

Goal: a functionally comparable music player built **without taking over source
code, resources or protected marks**.

## Separation

| Role | Input | Output |
|---|---|---|
| Analysis | public product docs, APK metadata, protocol research | feature/interface specification |
| Implementation | specification + public API documentation | original Kotlin/Compose code |

The implementation takes **no** application code and **no** resources from the
APK. APK analysis (manifest, version data, string inventory) serves
interoperability only: which sources and protocols have to be supported.

## Observed metadata of the reference APK

- Package: `app.symfonik.music.player`
- Version: `15.1.0 TV` (`versionCode 227812`)
- minSdk 26, targetSdk 36, compileSdk 37
- 4 ABIs, 13 native libraries (FFmpeg libraries, TagLib, Zstandard among them)
- 22 JSON layout templates for now-playing/app styles
- Permissions: network, foreground media playback, read audio, Bluetooth,
  billing, ads attribution

Requirements derived from this are **purely functional**: multiple sources,
configurable playback views, local files, server streaming, background
playback. No strings, layouts, artwork or code blocks were taken over.

## Protocol research

Supported sources are integrated through their public APIs:

- Jellyfin/Emby: REST (10.10.x)
- Subsonic/OpenSubsonic/Navidrome: `/rest/*.view` (1.16.1)
- Plex: plex.tv v2 PIN + PMS `/library/*`
- Audiobookshelf: REST `/api/*`
- Kodi: JSON-RPC
- Local: Android MediaStore

## Deliberate non-adoptions

- No Symfonium mark, logo or store listing text
- No copied colour resources, drawables, JSON styles or sounds
- No taking over of library internals or class names
- No circumvention of licensing or payment mechanisms

## Limits

Features and ideas are not protected by copyright; concrete expressions are.
The specification therefore describes behaviour (“album grid with cover, title,
artist”) and no concrete visual designs.
