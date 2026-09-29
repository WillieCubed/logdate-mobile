# Audio Recording on Wear OS

Voice capture is the primary input on the watch. The home screen is the recorder: one large button,
two ways to use it, and nothing between pressing it and being recorded.

## Using the recorder

| You do | What happens |
|--------|--------------|
| Tap the button | Recording starts and keeps going after you lift your finger. Tap again to stop and save. Pause and Discard sit on either side while it runs |
| Press and hold | Recording starts at once and saves when you let go, like a walkie-talkie |
| Stop | The note is saved immediately. For five seconds an **Undo** button removes it and its audio file |
| Record again during the Undo window | The previous note stays saved and the window closes |
| Close the app or swipe back out of the screen while recording | The recording is stopped and saved, the same as tapping stop. Sending the app to the background with the side button does not stop it |

A recording shorter than half a second is dropped as "Too short". Recordings stop on their own at 30
minutes, with a haptic warning a minute before, and are saved. That length is about 29 MB of audio, which
stays under the phone's 31 MiB upload limit.

Tapping the button while a tap-started recording is paused saves it; the button on its left resumes.
Someone using TalkBack gets a single action on the button that behaves like a tap.

A hint ("Tap to record or hold to talk") shows in place of the greeting until the first recording has
been saved.

## The screen states

`WearRecordingViewModel` drives the screen. Its phase moves through:

`READY` → `STARTING` → `RECORDING` ⇄ `PAUSED` → `SAVING` → `SAVED` → `READY`

with `TOO_SHORT` and `ERROR` as side exits. Every phase change happens **before** the recorder is asked
to do anything. That ordering is what stops the ViewModel from mistaking its own discard or stop for the
recorder ending the session by itself.

Errors say what failed and what to do:

| Error | Shown | Offered |
|-------|-------|---------|
| Microphone permission denied | "Microphone is off" | An Allow button |
| Not enough space for a full-length recording | "Watch storage is full" | |
| The recorder did not start | "Couldn't start. Try again" | Press again |
| The recording finished but could not be stored | "Couldn't save. Tap to retry" | Press again: it saves the same recording rather than starting a new one |
| The recorder produced no usable file | "Recording lost. Try again" | Press again to start a new recording |

A call or another app taking the audio pauses the recording, and the screen says "Paused by another
app". It does not resume by itself.

## Recording infrastructure

The watch reuses the phone's recording service, `AudioRecordingService` in `client:media`, rather than
keeping its own copy. It brings a confirmed start, error handling and file finalization that were
already exercised on the phone.

- **`WearAudioRecordingManager`** holds the session logic. Start reports success only once the service
  confirms the recorder is running. Stop returns only after the file is complete. Both run under one
  mutex, and both check the microphone permission and free space first.
- **`RecordingSessionOptions`** switch on watch-only behavior in the service without changing the
  phone: a maximum length (30 minutes), pausing when another app takes audio focus, and a partial wake
  lock so a dozing watch does not drop audio.
- **`StorageSpaceChecker`** measures free space where recordings are written, `filesDir`, and requires
  room for a full-length recording plus headroom.

### Audio format

| Parameter | Value |
|-----------|-------|
| Codec | AAC |
| Bitrate | 128 kbps |
| Sample rate | 44.1 kHz |
| Container | M4A |
| Size | about 960 KB per minute |

### File lifecycle

1. The service writes to `filesDir/audio_notes/recording_<id>.m4a`.
2. On save, a `JournalNote.Audio` is created in Room with that path and the duration read back from the
   file.
3. Discarding, an Undo, and a too-short recording delete the file. A file that a saved note references is
   never deleted.
4. The note reaches the phone over the Data Layer, and the watch keeps it pending until the phone
   acknowledges it. See the sync notes in [`app/wear/README.md`](../../app/wear/README.md).
5. An Undo also tells the phone. A note that is still pending is dropped from the watch's queue before
   it is ever sent, so the watch sends the phone an explicit delete, and the phone records that the note
   was deleted. Data items and channels are not ordered against each other, so a delete can arrive
   before the note it removes, and without that record the late note and audio would be stored anyway.

**Not covered yet:** a recording in progress when the watch app is killed leaves an unfinalized file that
nothing recovers. An MPEG-4 file has no index until the recorder finishes it, so it cannot be played
back, and recovering it would need a crash-safe output format.

## Haptics

Recording interactions use patterns from `WearHapticEngine`:

| Event | Pattern |
|-------|---------|
| Start recording | `startRecording()` |
| Stop recording | `stopRecording()` |
| Pause / resume | `pause()` / `resume()` |
| Too short, or an undo | `rejection()` |
| Saved | `success()` |
| A minute before the 30 minute limit | `warning()` |

## Permissions

| Permission | Required for |
|------------|-------------|
| `RECORD_AUDIO` | Microphone access |
| `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` | Recording that survives the screen turning off |
| `POST_NOTIFICATIONS` | The recording notification |
| `WAKE_LOCK` | Recording continues with the screen off |
| `VIBRATE` | Haptic feedback |

## Testing

```bash
# Unit tests: the recorder session, the ViewModel, the player
./gradlew :app:wear:testDebugUnitTest

# Every recorder state on small and large round displays
./gradlew :app:wear:validateDebugScreenshotTest
```

The microphone itself cannot be tested on an emulator. `docs/testing/wear-dogfood-checklist.md` lists what
to check on a real watch.
