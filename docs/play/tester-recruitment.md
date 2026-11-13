# Closed-test recruitment post

For r/BambuLab, the Bambu Lab forum and Bambu Discord servers. Google requires at least 12 testers
opted in for 14 days in a row before a new personal developer account can publish.

- Tester group: https://groups.google.com/g/printwatcher-testers
- Play opt-in link: https://play.google.com/apps/testing/com.das.printwatcher
  (live: phone release approved 2026-11-13; Wear OS release still in review)

## Title

Looking for testers: free Wear OS watch app that shows your Bambu print progress (LAN only, no cloud)

## Body

I made a small free app, **printWatcher**, that puts your Bambu Lab printer's status on a Wear OS
watch and your phone. It's ready for Google Play, but Google requires new developers to run a
closed test with at least 12 testers for 14 days first, so I'm looking for a few people to help.

**What it does**

- Watch tile with a progress ring (yellow printing, green done, red error), percent, job name,
  time left and layer
- Watch-face complication and a print-in-progress icon
- Alerts on the watch and phone: print finished, paused or stopped, errors and HMS alerts, first layer done, lost connection
- Phone screen with temperatures and AMS filament colours/amounts
- Finds your printer on the network automatically

**Private by design:** it talks to the printer directly on your local network (LAN access code).
No Bambu cloud account, no servers, no ads, no analytics. It's read-only: it never sends commands
that could affect a print. Tested on a P1S and a Galaxy Watch6; other Bambu models that support
LAN mode should work and I'd love to hear how they do.

**No watch? No problem.** The watch is optional. printWatcher runs completely on your phone, with
the full status screen and alerts there; the watch just adds the tile and wrist alerts. And if your
printer's busy, there's a demo mode that runs a simulated print, so you can still help.

**How to join (2 minutes)**

1. Join the tester group with the Google account you use for the Play Store:
   https://groups.google.com/g/printwatcher-testers
2. Opt in to the test: https://play.google.com/apps/testing/com.das.printwatcher
3. Install printWatcher from the Play Store link on that page.
4. **Please stay in the test for at least 14 days.** That's the part Google counts. You don't have
   to use it every day.

Feedback, bugs and "it doesn't work with my X1C" reports are very welcome here or on GitHub:
https://github.com/yaturner/printWatcher

It's open source (MIT) and not affiliated with Bambu Lab.

## r/BambuLab version (project share)

r/BambuLab removes posts that direct users to items or services for sale or that beg for items or
services, so this is framed as sharing a free project, with the closed-test invite as an optional
extra at the end. Use a "Self-Promotion"/"Project" flair if there is one (or ask the mods first),
attach a watch tile screenshot, and reply to comments for the first few hours. Drop the "watch app
is in Google's review" line once the Wear OS release is approved.

### Title

I made a free, open-source Wear OS app that shows your Bambu print progress on your watch (LAN only, no cloud)

### Body

I got tired of opening Bambu Handy every few minutes to check on long prints, so I built
**printWatcher**: a small Android and Wear OS app that puts your printer's status on your wrist and
phone. It's free, open source (MIT) and has no ads, accounts or tracking.

**What it does**

- Watch tile with a progress ring (yellow printing, green done, red error), percent, job name, time left and layer
- Watch-face complication and a print-in-progress icon
- Alerts on watch and phone: print finished, paused or stopped, errors/HMS alerts, first layer done, lost connection
- Phone screen with nozzle, bed and chamber temperatures and AMS filament colours and amounts
- Finds your printer on your network automatically during setup

**How it works**

- Talks to the printer **directly over your local network** with the LAN access code (MQTT over TLS). No Bambu cloud login, and nothing goes to any server.
- **Read-only**: it never sends anything that could affect a print.
- **The watch is optional.** It runs completely on your phone, with the full status screen and alerts there; the watch adds the tile and wrist alerts.
- There's a demo mode with a simulated print if you want to see it without a printer.

I've tested it on a P1S with a Galaxy Watch6. Other models with LAN mode (X1C, P1P, A1, A1 mini…)
should work, and I'd love to hear how it does on yours.

**Trying it early:** it's on its way to the Play Store, and while it's in Google's closed test
anyone can try it now by joining
[the tester group](https://groups.google.com/g/printwatcher-testers) with their Play Store Google
account and then [opting in here](https://play.google.com/apps/testing/com.das.printwatcher). The
phone app is available now; the watch app is in Google's review and will arrive automatically.

Source, issues and feature ideas: https://github.com/yaturner/printWatcher

Not affiliated with Bambu Lab. Happy to answer questions!

## Bambu Lab forum version

Post in the community projects / software category (check its pinned rules on posting your own
apps). Drag in 2–3 screenshots (watch tile, an alert, the phone screen) and watch the thread for
replies. Drop the "watch app is in Google's review" line once the Wear OS release is approved.

### Title

printWatcher: free Wear OS app for Bambu print status, testers wanted

(The forum limits titles to 80 characters.)

### Body

Hi all,

I've built a small free app, **printWatcher**, that shows your Bambu Lab printer's status on a
Wear OS watch and your Android phone. I wanted to glance at my wrist instead of opening Bambu
Handy every few minutes, so I made one.

Before Google will let a new developer publish, the app has to go through a closed test with **at
least 12 testers for 14 days**. I'd be very grateful for some help, and especially for reports from
printers I don't own.

**No watch? No problem.** The watch is optional. printWatcher runs completely on your phone, with
the full status screen and alerts there; the watch just adds the tile and wrist alerts.

#### What it does

- **Watch tile**: a progress ring (yellow printing, green done, red error) with percent, job name, time left and current layer
- **Watch-face complication** and a print-in-progress icon
- **Alerts** on watch and phone: print finished, paused or stopped, errors and HMS alerts, first layer done, lost connection
- **Phone screen** with nozzle, bed and chamber temperatures and AMS filament colours and remaining amounts
- **Automatic printer discovery** on your network during setup
- **Demo mode** with a simulated print, so you can try it without a printer (or a watch)

#### How it works (and what it doesn't do)

- It connects **directly to your printer over your local network** (MQTT over TLS) using the
  printer's **LAN access code**, so you need LAN mode turned on. Developer Mode isn't needed.
- **No Bambu cloud account**, no servers of mine, no ads, no analytics. Your access code stays on
  your phone (encrypted), and the watch only receives status.
- It's **read-only**: it never sends commands that could affect a print, so it's safe to leave
  running during long jobs.
- Your phone has to be on the same network as the printer. The watch gets updates from the phone.
- Privacy policy: https://projects.darcangel.com/bambuwatch/privacy/

#### Compatibility

Tested on a **P1S** with a **Galaxy Watch6** and a Samsung foldable. I'd expect the X1C, X1E, P1P,
A1 and A1 mini to work too, since they all support LAN mode, but I can't confirm. If you test it,
please reply with:

- printer model and firmware version
- phone and watch model
- anything that looked wrong (missing temps, wrong progress, alerts not arriving…)

#### How to join (about 2 minutes)

1. Join the tester group with the Google account you use for the Play Store: https://groups.google.com/g/printwatcher-testers
2. Opt in to the test: https://play.google.com/apps/testing/com.das.printwatcher
3. Install **printWatcher** from the Play Store link on that page.
4. **Please stay opted in for 14 days.** That's what Google counts. You don't have to use it every day.

The phone app is available now. The watch app is in Google's review and will appear automatically
for testers once it's approved.

Feedback is welcome in this thread or as a GitHub issue:
https://github.com/yaturner/printWatcher/issues. The source is on GitHub (MIT licence).

*printWatcher is a personal project and isn't affiliated with or endorsed by Bambu Lab.*

Thanks!
