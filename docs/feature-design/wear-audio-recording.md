# Audio Recording on Wear OS

Voice capture is the primary input mode on the watch. Two recording flows serve different
use cases — walkie-talkie for spontaneous capture, and the full recording studio for longer,
deliberate notes.

## Recording flows

### Walkie-talkie (push-to-talk)

Touch the screen, speak, release. The entire screen is the button.

| State | Behavior |
|-------|----------|
| **Ready** | Full-screen touch target, "HOLD TO RECORD" label |
| **Recording** | Red background, live waveform, timer counting up |
| **Saved** | Green checkmark, duration, auto-returns to home after 500ms |
| **Too short** | If held < 500ms, recording is discarded with "Too short / Hold longer" |
| **Error** | Displays error message (e.g., "Microphone unavailable") |

The 500ms minimum prevents accidental taps from creating empty notes. Maximum duration is
60 seconds — a warning haptic fires at 50s, and auto-stop triggers at 60s.

**ViewModel**: `WalkieTalkieViewModel` manages the state machine via `WalkieTalkiePhase`.
Touch-down starts recording, touch-up stops and saves.

### Full voice note

A traditional record/stop flow with pause/resume and cancel controls.

| State | Controls |
|-------|----------|
| **Idle** | Large red record button (64dp) |
| **Recording** | Waveform + timer + stop button, pause/cancel below |
| **Paused** | Resume/cancel buttons, waveform frozen |
| **Error** | Error message displayed above controls |

**ViewModel**: `AudioRecordingViewModel` (extends `AndroidViewModel`). Manages recording
lifecycle through `WearAudioRecordingManager`.

## Recording infrastructure

### Foreground service

`WearAudioRecordingService` is a foreground service that owns the `MediaRecorder`. It runs
independently of the UI so recording survives screen-off and app backgrounding.

Responsibilities:
- MediaRecorder lifecycle (prepare, start, pause, resume, stop, release)
- Wake lock management (partial wake lock during recording)
- Haptic feedback via the haptic engine (start, stop, pause, resume)
- Notification with recording indicator
- Audio level sampling for waveform visualization

### Recording manager

`WearAudioRecordingManager` bridges ViewModels and the bound foreground service. It
implements the shared `AudioRecordingManager` interface and exposes reactive state:
- Start, stop, pause, and resume controls
- Recording status, audio levels, and elapsed duration as `StateFlow`s

### Storage validation

`StorageSpaceChecker` verifies available space before starting a recording. The estimate is
based on 128kbps AAC at 44.1kHz — approximately 960KB per minute plus a 512KB buffer.
If space is insufficient, recording is blocked and the user sees an error.

### Audio format

| Parameter | Value |
|-----------|-------|
| Codec | AAC |
| Bitrate | 128 kbps |
| Sample rate | 44.1 kHz |
| Container | M4A |
| Estimated size | ~960 KB/min |

### File lifecycle

1. Recorded to a temp file in the cache directory
2. On successful save, moved to `app_files/audio/{noteId}.m4a`
3. A `JournalNote.Audio` is created in Room with the file path
4. Deleted when the associated note is removed

## Haptic feedback

Recording interactions use distinct patterns from `WearHapticEngine`:

| Event | Pattern | Feel |
|-------|---------|------|
| Start recording | `startRecording()` | Strong single pulse |
| Stop recording | `stopRecording()` | Double-tap confirmation |
| Pause | `pause()` | Light tick |
| Resume | `resume()` | Light double tick |
| Too short (walkie-talkie) | `rejection()` | Brief low-frequency buzz |
| Save confirmed | `success()` | Warm double-pulse |
| 50s warning (walkie-talkie) | `warning()` | Three quick pulses |

## Battery considerations

- AAC 128kbps balances quality and power consumption
- UI updates throttled to 100ms intervals (10 FPS waveform)
- Wake lock is partial (CPU only, screen can turn off)
- No continuous sensor polling during recording

## Permissions

| Permission | Required for |
|------------|-------------|
| `RECORD_AUDIO` | Microphone access |
| `FOREGROUND_SERVICE` | Background-capable recording |
| `FOREGROUND_SERVICE_MICROPHONE` | Foreground service type declaration |
| `WAKE_LOCK` | Recording continues with screen off |
| `VIBRATE` | Haptic feedback |

## Testing

Both recording flows have full test coverage:

**Unit tests** — `WalkieTalkieViewModelTest` (15 tests): state machine transitions, minimum
duration gate, maximum duration auto-stop, haptic pattern selection.

**Screenshot tests** — `WalkieTalkieScreenshots` (8 previews) and `AudioRecordingScreenshots`
(4 previews): every state rendered on small and large round displays.

**E2E instrumented tests** — `WalkieTalkieScreenTest` (14 tests) and
`AudioRecordingScreenTest` (8 tests): UI elements and callback verification with controlled
state, no service binding required.

```bash
# Run unit tests
./gradlew :app:wear:test --tests "*WalkieTalkieViewModelTest"

# Validate screenshots
./gradlew :app:wear:validateDebugScreenshotTest

# Run E2E tests (requires device)
./gradlew :app:wear:connectedAndroidTest
```
