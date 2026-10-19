# Halone Test Kit

A separate app for people who own glasses we don't. It runs a spoken,
step-by-step session and produces one zip to send back. No developer setup
needed: install the APK, tap Start, follow the voice.

```sh
./gradlew :testkit:assembleDebug   # android/testkit/build/outputs/apk/debug/testkit-debug.apk
```

## What a session does

1. Consent, then waits for the glasses to be plugged in and picks a profile by USB id.
2. Checks the glasses show a picture. The current instruction is mirrored onto
   them, so the tester can read it while wearing them.
3. Runs the profile. For the XREAL One series:
   - Asks the tester to set Follow mode and turn Stabilizer off.
   - If the glasses offer no network function over USB, walks the tester through
     turning TCP/IP on in the glasses' Developer page, waits for it, and reminds
     them at the end that they can turn it off again.
   - Connects to the report stream, over the phone's own network to the glasses
     if it brought one up, otherwise over the in-app USB network stack (after
     asking for USB access). On failure it retries, then asks about Stabilizer,
     then carries on without sensors.
   - Sends three read-only getters (config, firmware and DSP versions).
   - Records nine guided head motions. Each one is checked live from the gyro,
     and repeated if the tester didn't hold still or didn't move enough.
   - Works out the axis convention on the phone (`AxisSolver`), from how far the
     head got from where it was at each beep. Testers often make a small
     settling motion right after the beep and start the real one seconds later. Opposite motions
     give each head axis as the IMU sees it, and two axes fix the frame. The
     third axis and gravity check it: turning the wrong way both times flips
     gravity, and mixing up a pair breaks the third axis. Contradicted motions
     are repeated once. The result, plus the IMU's tilt against the head, goes
     to `analysis/axes.json`, next to the factory calibration's display rotation
     for comparison.
   - Runs blind A/B rounds when the axes came back confident or inferred and the
     factory calibration was read. A green world-locked grid replaces the
     instruction card on the glasses, tracked with the solved axes and Halone's
     `HeadTracker`. Each round shows two candidates as "first" and "second" in
     random order, and the answers bisect two parameters in three rounds each: the
     field of view (does the grid stick to the room) and the prediction horizon
     (does it lag or swing past). Results go to `analysis/ab.json`. A mount-pitch
     round was dropped after two wearers couldn't tell ±5° apart.
4. Walks through the display modes (3D, refresh rate) in the glasses' own menu,
   noticing each change and recording the mode list the phone sees.
5. Asks a few questions, takes an optional comment, and writes the zip.

Unknown glasses get the generic profile: descriptors, networks, display modes
and answers.

## The zip

- `manifest.json`: answers, per-step outcomes, timestamped markers, notes.
- `recon/<moment>/`: phone, USB descriptors, networks and displays at that moment.
- `analysis/axes.json`: the solved convention, mount angle, and the evidence.
- `analysis/ab.json`: each A/B round (which candidate was first, the answer) and
  the final estimates.
- `captures/usbnet.pcap`: link-level frames when the in-app stack was used. The
  first 3000 are kept whole, then everything but sensor data. Open it in Wireshark.
- `captures/*.cap`: raw TCP traffic, written only inside motion segments (the
  control port always). The format is in the manifest. Markers and chunks
  share one clock, so each recorded byte falls inside the step that produced it.

The glasses' serial (`FSN`) is replaced with `X`s of the same length in every
file. The USB serial isn't readable without permission, so it never appears.

Concatenating the inbound chunks of a `.cap` gives the raw stream a driver test
can replay. That's how `core-glasses` tests the One driver.

## Adding glasses

Write a `Profile` (see `XrealOneProfile`) and add it to the list in
`MainActivity`. Scripts are coroutines over `Kit`. Steps call
`session.segment`, `retrying` and `io.ask`, and record an outcome, so a report
reads without the audio. Test them against `FakeKit` on virtual time, as
`XrealOneProfileTest` does.

Never send anything that changes a setting on hardware nobody here can recover.

## Not yet

- Translations: all text is in `res/values/strings.xml`, and `tts_locale` picks
  the voice.
