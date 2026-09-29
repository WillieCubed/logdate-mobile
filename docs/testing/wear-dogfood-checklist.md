# Wear OS dogfood checklist

Emulators cannot test the microphone, the speaker, Bluetooth audio, or a real phone and watch
talking to each other, so those get checked here, on your own devices, from the Play internal track.
Nothing below installs from your computer to the watch: install from Play on both.

## Before you start

1. The Play Console steps in [Google Play Publishing](../reference/google-play-publishing.md#one-time-play-console-setup)
   are done and `LOGDATE_PLAY_WEAR_PUBLISH_ENABLED` is `true`.
2. A push to `main` has published both the phone build and the watch build. The watch build shows up
   under the Wear OS track in Play Console.
3. LogDate is installed on the phone from Play, and you are signed in.
4. LogDate is installed on the watch from the Play Store on the watch. Allow the microphone when asked.
5. An older watch install under the retired `app.logdate.wear` package can stay while you test; it is a
   separate app and does not interfere. It will not update in place, and it never synced to the phone,
   so any recordings still on it exist nowhere else. Play them back or export them before you
   uninstall it.

Bring the phone and watch within range and confirm the watch's Settings screen says it is connected.

## Recording

| Do this | You should see |
|---------|----------------|
| Tap the button, speak for 5 seconds, tap again | A stop haptic, then "Saved" with "Syncing to phone" and an Undo button |
| Press and hold, speak, let go | Recording only while held, then "Saved" |
| Tap and lift, then tap again after under half a second | "Too short", and nothing saved |
| Tap, pause, resume, stop | The timer stops while paused, and the saved length matches what you recorded |
| Tap, then Discard | Back to ready, nothing saved |
| Save a recording, then Undo within five seconds | The note is gone from the watch, and from the phone a few seconds later |
| Record, then swipe back out of the app before stopping | The recording is saved, not lost, and appears in Voice memories |
| Record, then press the side button, then reopen LogDate | The recording continued while the app was in the background, and the screen still shows it recording |
| Undo a recording with the phone out of range, then bring it back | The note never appears on the phone |
| Record for 5 minutes with the screen off | A full-length recording, with sound throughout |
| Keep a recording going past 29 minutes | A haptic warning, then a save at 30 minutes |

## Interruptions

| Do this | You should see |
|---------|----------------|
| Start a recording, then have someone call the phone or play audio on the watch | "Paused by another app". Resuming continues the same recording |
| Turn microphone permission off in the watch's settings, then press the button | "Microphone is off" with an Allow button |

## Phone and sync

| Do this | You should see |
|---------|----------------|
| Record on the watch with the phone nearby | The note appears in the phone's timeline with playable audio within a minute, and the phone backs it up |
| Turn on airplane mode on the phone, record on the watch, then turn it off | The recording arrives on the phone after the phone comes back in range, and plays |

## Voice memories

| Do this | You should see |
|---------|----------------|
| Open the headphones button | Every recording, newest first, including ones the phone made |
| Tap a memory | It starts playing at once, with the time in the title |
| Pause, resume, skip back and forward 10 seconds | The progress bar follows |
| Play with no headphones connected on a watch with no speaker | "Connect headphones to listen", and the button opens Bluetooth settings |
| Play with the phone out of range a memory the watch never received | "Couldn't load audio. Tap to retry" |

## Known gaps

These are known and should not be reported again as new bugs:

- A recording that is in progress when the watch app is killed is lost.
- The tile's Mic and Rec buttons both open the home recorder.
- Activity-aware location in Settings does not run on the watch.
