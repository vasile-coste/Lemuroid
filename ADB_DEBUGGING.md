# Connecting over Wi-Fi (Wireless debugging)

Lemuroid targets a phone that usually isn't plugged in via USB. Android's "Wireless debugging"
developer option lets `adb` find and connect to it over the local network instead.

## 1. Enable it on the phone (one-time)
Settings → Developer options → **Wireless debugging** → toggle on.

## 2. Discover the phone from your machine
With the Wireless debugging screen open on the phone (it only broadcasts while that screen is
visible, or briefly after toggling on), run:

```bash
adb mdns services
```

This lists mDNS services the phone is advertising, e.g.:

```
adb-RFGYB33VD9E-31VH0R	_adb-tls-pairing._tcp	192.168.3.30:41235
adb-RFGYB33VD9E-31VH0R	_adb-tls-connect._tcp	192.168.3.30:43371
```

- `_adb-tls-pairing._tcp` — only present, and only needed, the **first time** you connect from a
  given machine.
- `_adb-tls-connect._tcp` — used every time after pairing.

The IP and port on both can change between sessions (Wi-Fi reconnects, phone reboots, adb daemon
restarts) — always re-run `adb mdns services` rather than reusing an address from a previous
session.

## 3. First-time pairing (skip if already paired)
1. On the phone: Settings → Developer options → Wireless debugging → **Pair device with pairing
   code**. This shows a 6-digit code and keeps the pairing service alive while the dialog is open.
2. Take the `_adb-tls-pairing._tcp` address from `adb mdns services` and run:
   ```
   adb pair <pairing-ip>:<pairing-port> <6-digit-code>
   ```

## 4. Connect (every session)
```bash
adb connect <connect-ip>:<connect-port>
```
using the address from `_adb-tls-connect._tcp`. Verify with:
```
adb devices -l
```

If multiple devices/emulators are attached, target this one explicitly in every command below with
`-s <ip>:<port>`, e.g. `adb -s 192.168.3.30:43371 install app.apk`.

---

# Debugging commands used in day-to-day Lemuroid work

The debug build's application ID is `com.swordfish.lemuroid.debug` (the release/Play build uses
`com.swordfish.lemuroid` — check `lemuroid-app/build.gradle.kts` if this ever changes). Substitute
accordingly below.

## Installing a freshly built APK
```bash
./gradlew :lemuroid-app:assembleFreeBundleDebug
adb install -r lemuroid-app/build/outputs/apk/freeBundle/debug/lemuroid-app-free-bundle-debug.apk
```
`-r` reinstalls over the existing app, keeping its data (Room DB, preferences, saves) — this is
what you want when iterating.

## Confirming the app is installed
```bash
adb shell pm list packages | grep -i lemuroid
```
for multiple devices just use
```bash
adb -s 192.168.3.30:43371 shell pm list packages | grep -i lemuroid
```

## Watching logs while reproducing a bug
Clear old logs first so you're only looking at fresh output, then filter to the app's process:
```bash
adb logcat -c
# ... reproduce the bug on the phone ...
adb logcat -d --pid=$(adb shell pidof com.swordfish.lemuroid.debug) > lemuroid.log
```
or for multiple devices
```bash
adb -s 192.168.3.30:43371 logcat -c
# ... reproduce the bug on the phone ...
adb -s 192.168.3.30:43371 logcat -d --pid=$(adb -s 192.168.3.30:43371 shell pidof com.swordfish.lemuroid.debug) > lemuroid.log
```
`-d` dumps the current buffer and exits instead of streaming, which is usually what you want when
capturing a single repro. Search the dump for `Timber`/your tag, `Exception`, or the specific class
you touched (e.g. `GameInteractor`) rather than reading it all — Samsung/Knox devices in particular
emit a lot of unrelated `StrictMode`/`EDMProxy` noise.

## Inspecting the app's Room database
Debug builds are `run-as`-able, so you can read their private storage without root:
```
adb shell run-as com.swordfish.lemuroid.debug ls databases/
adb exec-out run-as com.swordfish.lemuroid.debug cat databases/retrograde > /tmp/retrograde.db
sqlite3 /tmp/retrograde.db "SELECT id, fileName, fileUri FROM games LIMIT 5;"
```
Useful for checking what's actually persisted (e.g. a game's `fileUri` scheme — `content://...`
means it was indexed via Storage Access Framework, `file://...` means the legacy local-file
provider) instead of guessing from code alone.

## Checking granted Storage Access Framework permissions
`adb shell dumpsys uri_grants` exists but returns empty output on newer Android versions (seen on
Android 16/SDK 36) even when grants exist — don't treat an empty result as proof of "no
permission granted." When in doubt, reproduce the actual behavior on-device instead (e.g. does a
file delete/write actually succeed) rather than relying on this dump.

## Uninstalling (e.g. to test a truly clean install)
```
adb uninstall com.swordfish.lemuroid.debug
```
This wipes the app's data, including any persisted SAF folder permission — you'll need to
re-run the ROMs folder picker afterward.

## Screenshots
```
adb exec-out screencap -p > /tmp/screen.png
```
