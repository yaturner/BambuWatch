# Google Play release kit — printWatcher for Bambu Lab

Everything to paste into Play Console, plus the upload steps. Images are in this folder.

## Build

```
./gradlew :mobile:bundlePlayRelease :wear:bundleRelease
```

| File | Upload to |
|---|---|
| `mobile/build/outputs/bundle/playRelease/mobile-play-release.aab` | Phone track (Production / testing) |
| `wear/build/outputs/bundle/release/wear-release.aab` | **Wear OS** track (same app listing) |

Both are signed with the upload key from the gitignored `keystore.properties`
(`~/keystores/printwatcher-upload.jks`). Enroll in **Play App Signing** when creating the app; Google
holds the app signing key, you keep the upload key. Version codes: phone `appVersionCode × 10`,
watch `× 10 + 1` (set in `gradle.properties`); bump `appVersionCode` for every release.

## Store listing

**App name** (≤30): `printWatcher for Bambu Lab`

**Short description** (≤80):
`Bambu Lab print progress and alerts on your Wear OS watch. Local network only.`

**Full description**:

```
printWatcher shows your Bambu Lab 3D printer's progress on your Wear OS watch and phone, and
alerts you when a print finishes or needs attention.

ON YOUR WATCH
• Tile with a progress ring: green when idle or done, yellow while printing, red on an error —
  plus percent, job name, time left and layer.
• Watch-face complication: progress ring, percent with time left, or a one-line summary.
• Print-in-progress icon on the watch face while a print runs.
• Alerts: print finished, stopped, paused, errors, HMS alerts, first layer done, lost connection.

ON YOUR PHONE
• Live status: progress, time left, layer, nozzle and bed temperatures, AMS filament colors and
  amounts.
• Background monitoring with notifications, even when the app is closed.

PRIVATE BY DESIGN
• Connects directly to your printer on your local network. No Bambu cloud account, no third-party
  servers, no ads, no analytics.
• Read-only: printWatcher never sends commands that could affect a print.

REQUIREMENTS
• A Bambu Lab printer on your local network and its LAN access code (on the printer:
  Settings → Network). Tested with the P1S.
• A Wear OS 3+ watch paired with your phone (tested with Galaxy Watch6).

printWatcher is an independent app and is not affiliated with or endorsed by Bambu Lab.
```

**Category**: Tools · **Tags**: 3D printing, Wear OS, smartwatch
**Contact email**: thejmturner@pm.me · **Website**: https://projects.darcangel.com/bambuwatch/
**Privacy policy**: https://projects.darcangel.com/bambuwatch/privacy/

**Graphics**

| Asset | File |
|---|---|
| App icon 512×512 | `icon-512.png` |
| Feature graphic 1024×500 | `feature-graphic-1024x500.png` |
| Phone screenshots | `phone-1-print-status.png`, `phone-2-watch-link.png`, `phone-3-setup.png` |
| Wear OS screenshots (1:1, unmasked) | `wear-1-tile-printing.png`, `wear-2-tile-done.png`, `wear-3-app.png` |

## App content (Policy → App content)

**Privacy policy**: URL above.

**Ads**: No ads.

**App access**: *All or some functionality is restricted* → add instructions:
```
printWatcher normally needs a Bambu Lab 3D printer on the same local network. To review
without one, use the built-in demo mode (no login or account is needed):

1. Open printWatcher on the phone. On the "Connect to your printer" screen, scroll down and
   tap "Try demo mode". Allow notifications when asked.
2. The app shows a simulated printer. A 5-minute cycle repeats: preparing, printing, a paused
   print with an alert, finished and idle. Alerts ("First layer done", "Printer needs
   attention", "Print finished") appear as notifications.
3. Wear OS: install printWatcher on a watch paired with the phone. Add the "printWatcher" tile
   and the complication. They show the same simulated print within a few seconds (the ring is
   yellow while printing, red during the alert, green when finished or idle).
4. To leave demo mode, tap "Exit demo" on the status screen.
```

**Content rating** (IARC questionnaire): category *Utility, productivity, communication or other*;
answer **No** to everything (no violence, sexual content, gambling, user-generated content, sharing
of location, purchases). Expected rating: Everyone / PEGI 3.

**Target audience**: 18 and over (not designed for children). Not in the Families program.

**News app**: No. **Government app**: No. **Financial features**: None. **Health**: None.

**Data safety**:
- *Does your app collect or share any of the required user data types?* **No.**
  (Printer settings and status are stored only on the device and never sent to the developer.
  Phone→watch sync uses Google Play services' Wear OS Data Layer between the user's own devices.)
- *Is all user data encrypted in transit?* Yes (printer connection is TLS).
- *Do you provide a way for users to request that their data is deleted?* Not applicable —
  no data is collected; uninstalling removes everything.

**Foreground service permissions** (declaration for `FOREGROUND_SERVICE_CONNECTED_DEVICE`):
- Task type: *Connected device*.
- Description:
  ```
  printWatcher keeps a connection to the user's Bambu Lab 3D printer on their local network
  (MQTT over TLS) while a print is being monitored, so it can notify the user and their watch
  as soon as the print finishes, fails or pauses. The service shows an ongoing notification
  with print progress and a Stop monitoring action, and the user can turn monitoring off in the
  app at any time.
  ```
- Video: required — record the phone showing the ongoing notification with progress, the app's
  "Background monitoring" switch, and a finish alert arriving (upload to YouTube unlisted, paste link).

## Wear OS

1. Play Console → **Test and release → Advanced settings → Form factors → Add form factor → Wear OS**,
   opt in to Wear OS distribution.
2. Upload `wear-release.aab` to the **Wear OS** track (closed testing first is easiest).
3. Wear OS screenshots go in the listing's Wear OS section.
4. Wear review checks: the watch app is not standalone (`com.google.android.wearable.standalone=false`)
   and needs the phone app; it shows "Waiting for phone… Open printWatcher on your phone" when no data.

## New personal developer accounts

Google requires new personal accounts to run a **closed test with at least 12 testers for 14 days**
before production access. Plan for that before the public launch.

## Release notes (1.0.0)

```
First release: print progress tile, watch-face complication, print-in-progress icon and alerts
for Bambu Lab printers on your local network.
```
