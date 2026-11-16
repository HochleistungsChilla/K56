# K56 — Testing mit Android-Emulator

So testen wir die App reproduzierbar auf einem virtuellen Gerät (kein echtes
Handy nötig). Setup, Bedienung, Messung und die Reproduktion des
Such-Absturzes in einem Ablauf.

## 1. Voraussetzungen

- Android SDK unter `/home/fran/Android/Sdk` (`platform-tools/adb`,
  `cmdline-tools/latest/bin/{sdkmanager,avdmanager}`).
- Java 21: `JAVA_HOME=/home/fran/tools/jdk-21.0.12.1+1`
  (oder `export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))`).
- `/dev/kvm` muss verfügbar sein (Hardware-Beschleunigung).
- Kein `adb` im PATH → immer den absoluten Pfad nutzen:

```bash
ADB=/home/fran/Android/Sdk/platform-tools/adb
export ANDROID_HOME=/home/fran/Android/Sdk
export ANDROID_SDK_ROOT=/home/fran/Android/Sdk
export JAVA_HOME=/home/fran/tools/jdk-21.0.12.1+1
```

## 2. Emulator + System-Image installieren (einmalig)

```bash
yes | /home/fran/Android/Sdk/cmdline-tools/latest/bin/sdkmanager \
  --sdk_root=/home/fran/Android/Sdk \
  "emulator" "system-images;android-34;google_apis;x86_64"
```

AVD anlegen (Pixel 6, Android 14):

```bash
echo no | /home/fran/Android/Sdk/cmdline-tools/latest/bin/avdmanager \
  create avd -n k56-test \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_6
```

## 3. Emulator headless starten

```bash
mkdir -p /tmp/k56-emu
setsid /home/fran/Android/Sdk/emulator/emulator \
  -avd k56-test -no-window -no-audio -no-snapshot \
  -gpu swiftshader_indirect -no-boot-anim \
  > /tmp/k56-emu/emulator.log 2>&1 < /dev/null &

$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  sleep 5
done
```

## 4. Bauen, installieren, starten

```bash
cd /home/fran/.openclaw/workspace/k56
./gradlew assembleDebug
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n app.k56.player/.MainActivity
```

Beim ersten Start erscheint der Benachrichtigungs-Dialog: „Allow" antippen
(~Mitte `540,1304`).

## 5. Bedienen & messen

```bash
# Screenshot
$ADB exec-out screencap -p > /tmp/k56-emu/screen-XX.png

# UI-Baum für Koordinaten
$ADB shell uiautomator dump /sdcard/ui.xml && $ADB pull /sdcard/ui.xml /tmp/k56-emu/ui.xml

# Tippen / Wischen
$ADB shell input tap X Y
$ADB shell input swipe X1 Y1 X2 Y2 300

# Text eingeben (Feld vorher antippen; danach Keyboard mit keyevent 4 schliessen!)
$ADB shell input text 'begriff'
$ADB shell input keyevent 4

# Cold-Start-Zeit
$ADB shell am force-stop app.k56.player
$ADB shell am start -W -n app.k56.player/.MainActivity   # TotalTime in ms

# Logcat
$ADB logcat -c            # vorher leeren
$ADB logcat -d > /tmp/k56-emu/log.txt   # danach auslesen
```

**Wichtig beim Texteingeben:** Nach jedem `input text` das Keyboard mit
`keyevent 4` schliessen, sonst verrutschen die Feld-Koordinaten und alles
landet im selben Feld (einmal beobachtet: `10.0.2.2:8096ytestergpw`).

## 6. Test ohne echten Server (Mock-Jellyfin)

Die App braucht eine Server-Anmeldung. Ohne echten Jellyfin reicht ein
Minimal-Mock (Python-Stdlib, keine Dependencies): `tools/mock_jellyfin.py`.
Er antwortet auf Login (`POST /Users/AuthenticateByName`), `System/Info/Public`
und `/Items` (Suche). Vom Emulator aus ist der Host unter `10.0.2.2` erreichbar.

```bash
setsid bash -c 'python3 tools/mock_jellyfin.py 8096 > /tmp/k56-emu/mock.log 2>&1' \
  < /dev/null > /dev/null 2>&1 &
curl -sS http://127.0.0.1:8096/System/Info/Public   # sanity
```

Anmeldung im Emulator: Backend **Jellyfin**, LAN-Adresse `10.0.2.2:8096`,
Benutzer `tester`, Passwort beliebig (z. B. `pw`).

## 7. Such-Absturz reproduzieren (und Fix)

**Ursache:** Jellyfin-Suche kann dasselbe Item mehrfach liefern (ein Album, das
sowohl direkt als auch als Container eines Treffer-Tracks gematcht wird). Die
Ergebnislisten nutzten `LazyColumn` mit `key = { it.id }`; eine doppelte ID
warf `IllegalArgumentException: Key "…" was already used` und beendete die App.

**Reproduktion:** Suche nach `dup` (der Mock liefert zwei Alben mit derselben
ID `album-1`). Vor dem Fix: sofortiger Crash mit obigem Stacktrace in
`logcat -d` unter `FATAL EXCEPTION`.

**Fix:** Ergebnisse vor dem Rendern per ID deduplizieren
(`SearchScreen.kt`, `distinctBy { it.id }` für Alben/Interpreten/Titel).
Verifiziert: `dup`-Suche crasht nicht mehr und zeigt das Album nur noch einmal.

## 8. Bekannte Punkte

- Kein echter Server: die Home-Ansicht zeigt „Server unreachable", sobald der
  Mock stirbt. Mock-Prozess ist an die startende Shell gebunden — mit
  `setsid … &` plus Redirect vom Exec loslösen, sonst stirbt er mit der Session.
- `uiautomator dump` braucht ~2–3 s und verfälscht grobe Zeitmessungen der UI;
  für exakte Latenz lieber Logcat-Timestamps oder `am start -W` (nur Cold Start).
