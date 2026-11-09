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
| `:mobile` | Android app. Setup screen (IP / serial / access code, code encrypted via Android Keystore), `PrinterRepository` (HiveMQ MQTT client, reconnect with backoff, `pushall` every 60 s), live status screen. |

## Milestones

- [x] 1. `:shared` state model + merge logic + tests (`./gradlew :shared:test`)
- [x] 2. Phone MQTT client + setup screen, live status
- [ ] 3. Foreground service + finish/error notifications
- [ ] 4. Data Layer publish + `:wear` module
- [ ] 5. Tile
- [ ] 6. Complication + Ongoing Activity
- [ ] 7. Watch → printer commands (needs LAN-only + Developer Mode on current firmware)

## Build

```
./gradlew :shared:test :mobile:assembleDebug
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

## Notes

- MQTT parsing and client approach adapted from `p1sTouchScreen-Android`.
- Publishes use QoS 0: the printer's broker never PUBACKs QoS 1.
- TLS currently trusts the printer's self-signed cert unconditionally (LAN only). TODO: pin the fingerprint.
- `mc_remaining_time` is treated as minutes; confirm against a live print.
