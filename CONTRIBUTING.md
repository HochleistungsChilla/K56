# Mitmachen

K56 ist ein Cleanroom-Projekt. Das heißt: Ideen, Protokolle und öffentliche
API-Dokumentation dürfen als Grundlage dienen, **fremder Code, fremde Assets
und fremde Marken nicht**. Wer etwas beiträgt, bestätigt, dass der Beitrag
eigene Arbeit ist oder aus einer Quelle stammt, die das erlaubt.

## Bauen

```bash
git clone https://github.com/HochleistungsChilla/K56.git
cd K56
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

Voraussetzungen: JDK 17+ (getestet mit 21), Android SDK 35. Der Gradle
Wrapper lädt Gradle 8.11.1 selbst nach.

## Pull Requests

- Klein und thematisch geschlossen. Ein PR = ein Grund.
- Build und Unit-Tests müssen grün sein (CI läuft automatisch).
- Neue Backends gehören hinter `MediaRepository` — die UI bleibt unverändert.
- Öffentliche Verhaltensänderungen bitte in `docs/` oder im README nachziehen.
- Deutsch oder Englisch, beides ist willkommen.

## Was nicht angenommen wird

- Code oder Assets aus proprietären Playern (auch nicht „nur ein bisschen“).
- Dekompilierter Quelltext als Vorlage für Implementierungen.
- Telemetrie, Werbe-SDKs, Analytics ohne ausdrückliche Diskussion.

## Lizenz

Beiträge stehen unter **GPL-3.0-or-later** (siehe [LICENSE](../LICENSE)).
Mit dem Absenden eines Pull Requests stimmst du dieser Lizenz für deinen
Beitrag zu.
