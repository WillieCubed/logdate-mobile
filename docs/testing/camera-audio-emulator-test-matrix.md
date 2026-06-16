# Camera And Audio Emulator Test Matrix

This matrix covers the emulator and Gradle Managed Device paths that can validate the
`camera-audio-adaptive-quality-checklist.md` rows without physical hardware.

Use this for UI/UX verification, selector state, route handoff copy, preview/state sync, and
background-playback notification behavior. Keep real USB camera, USB microphone, wired headset,
and Bluetooth peripheral proof as manual evidence when the emulator cannot model the device
itself.

## What the emulator can prove

- Camera switcher UI is visible, accessible, and stable across layouts.
- Built-in camera switching updates the preview/state gate.
- Wear remote camera mirrors phone camera selection and result acknowledgement.
- Audio selector UI is visible for input/output and keeps the system-controlled route action.
- Background playback continues while the app is not visible, and the notification/session state
  stays synchronized.
- Wear output routing UI can fall back to Bluetooth settings when Wear OS owns routing.

## What still needs hardware evidence

- A real external USB camera preview/capture matrix.
- A real USB microphone, headset microphone, or Bluetooth microphone capture matrix.
- A real wired, USB, or Bluetooth output device matrix on phone or Wear.

## Recommended execution order

1. Run the shared selector UI test on managed devices:

```bash
./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.MediaDeviceSelectorE2ETest \
  --console=plain --no-build-cache
```

2. Run phone audio background playback on managed devices:

```bash
./gradlew :app:android-main:smokeDevicesGroupDebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.AudioPlaybackBackgroundE2ETest \
  --console=plain --no-build-cache
```

3. Run the Wear playback route/state flow on the Wear managed device:

```bash
./gradlew :app:wear:wearSmallRoundApi34DebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.wear.e2e.WearDayDetailPlaybackTest \
  --console=plain --no-build-cache
```

4. Run the pure state tests that do not need a device:

```bash
./gradlew :client:media:desktopTest \
  :client:media:testAndroidHostTest \
  :app:wear:testDebugUnitTest \
  --console=plain --no-configuration-cache
```

5. Use an Android Emulator AVD with camera mapping enabled for camera-switcher inspection:

- Map one camera slot to the host webcam.
- Map the second slot to the emulator photo/video source.
- Verify the compact selector and expanded sheet still select front/back rows correctly.
- Verify the preview stream gate waits for a fresh false-to-true transition after switching.

6. Use the Wear OS emulator with Bluetooth audio emulation for output routing inspection:

- Verify the output picker shows the current route and the Bluetooth fallback action.
- Verify the watch UI stays readable when Wear OS owns routing or no output is available.

## Execution results

Automated steps 1–4 were executed on Gradle Managed Device emulators on 2026-06-15. Steps 5–6 are
manual emulator inspections and were not run by automation.

| Step | Task / suite | Device(s) | Result |
| --- | --- | --- | --- |
| 1 | `MediaDeviceSelectorE2ETest` | `flagshipPhoneApi36` (Pixel 9 Pro, API 36) + `largeScreenTabletApi35` (Pixel Tablet, API 35) | Pass — 3/3 on both devices |
| 2 | `AudioPlaybackBackgroundE2ETest` | `flagshipPhoneApi36` + `largeScreenTabletApi35` | Pass — 2/2 on both devices (see note on the clip-length fix) |
| 3 | `WearDayDetailPlaybackTest` | `wearSmallRoundApi34` (Wear OS small round, API 34) | Pass — 14/14 |
| 4 | `:client:media:desktopTest`, `:client:media:testAndroidHostTest`, `:app:wear:testDebugUnitTest` | Host JVM (no device) | Pass |
| 5 | Manual AVD camera-mapping inspection | Android emulator AVD | Manual — not run by automation |
| 6 | Manual Wear Bluetooth-routing inspection | Wear OS emulator | Manual — not run by automation |

Notes:

- Step 2, tablet: an earlier run had `playbackNotificationAndSessionStaySynchronizedAfterBackgroundAndLock`
  failing **reproducibly** on the Pixel Tablet (API 35) emulator with `IllegalStateException: Timed out
  waiting for notification pause action becomes available after wake`. Logcat analysis showed this was a
  test-timing defect, not a tablet keyguard / notification-shade modeling gap: the 90-second silent clip
  was too short to outlast the test's own timeline (a 65-second background sleep plus the
  notification-shade fallback search, which runs slower on the tablet). Playback reached the end of the
  clip and went `STOPPED` mid-test, so the media notification flipped to a "Play" action and the
  pause-action assertion timed out. The phone passed only because its faster control-tap path finished
  with ~16 seconds of clip to spare. The fix lengthens the clip to 300 seconds
  (`AudioPlaybackBackgroundE2ETest.kt`), after which both devices pass 2/2. This is consistent with the
  `T-Audio_Background_Playback` tablet `[pass]` claim in `camera-audio-adaptive-quality-checklist.md`.
- Step 3: the `:app:wear` task does not honor `-Plogdate.androidTestClass`, so the whole Wear androidTest
  suite ran; `WearDayDetailPlaybackTest` passed all 14 cases. Unrelated Wear suites
  (`MoodCheckInScreenTest`, `WearRecordingScreenTest`) failed with the Wear round-emulator "Failed to
  inject touch input" limitation and are out of scope for this matrix.

## Evidence mapping

- `T-Camera_Switcher`: emulator-backed proof covers selector UI, built-in camera switching, preview
  gate behavior, and Wear phone-camera mirroring — executed via `MediaDeviceSelectorE2ETest` (phone +
  tablet) and `WearDayDetailPlaybackTest` (Wear). Manual hardware evidence is still required for the
  external USB camera matrix.
- `T-Audio_Switcher`: emulator-backed proof covers selector UI, system-controlled output handoff, and
  Wear Bluetooth fallback copy — executed via `MediaDeviceSelectorE2ETest` (phone + tablet) and
  `WearDayDetailPlaybackTest` (Wear, output picker + Bluetooth-settings fallback). Manual hardware
  evidence is still required for USB/Bluetooth input and output devices.
- `T-Audio_Background_Playback`: emulator-backed proof covers background playback continuation and
  notification/session sync — executed via `AudioPlaybackBackgroundE2ETest` (full pass on both the phone
  and tablet emulators after the clip-length fix, see Execution results). Manual hardware evidence is
  still required for real wired/USB/Bluetooth output devices.
