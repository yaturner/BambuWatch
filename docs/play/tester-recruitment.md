# Closed-test recruitment post

For r/BambuLab, the Bambu Lab forum and Bambu Discord servers. Google requires at least 12 testers
opted in for 14 days in a row before a new personal developer account can publish.

- Tester group: https://groups.google.com/g/printwatcher-testers
- Play opt-in link: https://play.google.com/apps/testing/com.das.printwatcher
  (works once the closed-test release has been reviewed and is live)

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

**Don't have a watch, or your printer's busy?** It has a demo mode that runs a simulated print, so
you can still help.

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
