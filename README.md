# BambuWatch

Galaxy Watch (Wear OS) companion for a Bambu Lab printer. The phone keeps a single
LAN MQTT connection to the printer and (from milestone 4) relays a compact state
snapshot to a watch tile over the Wearable Data Layer.

```
Printer ◄─MQTT/TLS :8883─► Phone (:mobile) ─Data Layer─► Watch (:wear, M4+)
```

## Modules

| Module | What |
|---|---|
| `:shared` | Pure Kotlin/JVM. `PrinterState`, `ReportParser` (merges partial MQTT reports), `WatchState` (phone→watch payload), `PrinterCommands`, Data Layer paths. Unit-tested. |
| `:wear` | Wear OS app (same applicationId as the phone app). `DataLayerListenerService` receives `/printer/state`, `WatchStateCache` keeps the latest snapshot, simple Compose status screen. Declares the `bambuwatch_wear` capability so the phone can tell it's installed. |
| `:mobile` | Android app. `PrinterMonitorService` (connectedDevice foreground service, restarts on boot) turns `PrintEventDetector` events into notifications that Wear OS mirrors to the watch. Setup screen (IP / serial / access code, code encrypted via Android Keystore), `PrinterRepository` (HiveMQ MQTT client, reconnect with backoff, `pushall` every 60 s), live status screen. |

## Milestones

- [x] 1. `:shared` state model + merge logic + tests (`./gradlew :shared:test`)
- [x] 2. Phone MQTT client + setup screen, live status
- [x] 3. Foreground service + finish/error notifications
- [x] 4. Data Layer publish + `:wear` module (phone pacing by `WatchSyncPolicy`: important changes immediately, progress every 30 s, heartbeat every 5 min)
- [ ] 5. Tile
- [ ] 6. Complication + Ongoing Activity
- [ ] 7. Watch → printer commands (needs LAN-only + Developer Mode on current firmware)

## Build

```
./gradlew :shared:test :mobile:assembleDebug
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

### Installing on the watch (Galaxy Watch, Wi-Fi debugging)

1. Watch: Settings → About watch → Software information → tap **Software version** 5 times.
2. Settings → Developer options → turn on **ADB debugging** and **Wireless debugging**.
3. Wireless debugging → **Pair new device**: note the IP:port and pairing code, then on the computer:
   ```
   adb pair <ip>:<pair-port> <code>
   adb connect <ip>:<port>          # the port shown on the main Wireless debugging screen
   adb -s <ip>:<port> install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```
Both apps must be signed with the same key (the same machine's debug key is fine) for the Data Layer to connect them.

## Printer settings

Settings (IP, serial, access code) are kept in three places, in this order of priority:

1. **`Documents/BambuWatch/printer.json` on the phone**: the main copy once you tap *Allow file access*
   (Android's "All files access", needed to read the file after a reinstall or an `adb push`).
   It survives clearing app data and uninstalling. Saving in the app rewrites it; hand edits are
   loaded on the next launch. If the file is invalid it's ignored, left untouched, and the app shows why.
   ```json
   { "ip": "192.168.1.50", "serial": "01P00A123456789", "accessCode": "12345678" }
   ```
   Push one from a computer: `adb push printer.json /sdcard/Documents/BambuWatch/printer.json`
2. **App storage** (access code encrypted with the Android Keystore): used when file access isn't granted.
3. **`printer.properties`** in the repo root (gitignored, see `printer.properties.example`): fills in
   **debug** builds when neither of the above has settings. Release builds never include it.

## Notes

- Notification channels (each can be tuned in Android settings): Print monitor (ongoing, silent, phone-only), Print finished, Print problems, Print milestones (first layer), Connection.
- The monitor service runs whenever monitoring is on, even when the printer is idle: Android 12+ blocks starting a foreground service from the background, so it can't sleep and wake itself when a print starts.
- Bambu reports a user-cancelled print as FAILED, so cancelling shows "Print stopped".

- MQTT parsing and client approach adapted from `p1sTouchScreen-Android`.
- Publishes use QoS 0: the printer's broker never PUBACKs QoS 1.
- TLS currently trusts the printer's self-signed cert unconditionally (LAN only). TODO: pin the fingerprint.
- `mc_remaining_time` is treated as minutes; confirm against a live print.
