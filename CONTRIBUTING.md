# Contributing

K56 is an independent project, inspired by Symfonium, YouTube Music and
ViPER4Android/JamesDSP. Ideas, protocols and public API documentation are fair
game; **other people's code, assets and trademarks are not**. By contributing
you confirm that your work is your own or comes from a source that permits it.

## Building

```bash
git clone https://github.com/HochleistungsChilla/K56.git
cd K56
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

Requirements: JDK 17+ (tested with 21), Android SDK 35. The Gradle wrapper
fetches Gradle 8.11.1 by itself.

## Pull requests

- Small and self-contained. One PR = one reason.
- Build and unit tests must be green (CI runs automatically).
- New backends belong behind `MediaRepository` — the UI stays untouched.
- User-visible behaviour changes get a note in `docs/` or the README.
- German or English, both are welcome.

## UI strings

All user-facing text lives in string resources:

- `app/src/main/res/values/strings.xml` — English (default)
- `app/src/main/res/values-de/strings.xml` — German

Add new text to both files. Never hard-code UI strings in Kotlin; the only
exception is log/debug output and exception messages that are not shown to users.

## What is not accepted

- Code or assets from proprietary players (not even “just a little”).
- Decompiled source used as a template for implementations.
- Telemetry, advertising SDKs or analytics without prior discussion.

## License

Contributions are licensed under **GPL-3.0-or-later** (see [LICENSE](../LICENSE)).
Sending a pull request agrees to that license for your contribution.
