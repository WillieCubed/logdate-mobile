# `:app:wear` — Wear OS App

LogDate on your wrist. Tap to record a thought, or press and hold to talk; log your mood with an
emoji; and listen back to your voice memories, all without pulling out your phone.

**Min SDK**: 31 (Wear OS 3+) | **Target SDK**: 37 | **Compile SDK**: 37

## The watch and the phone are one app

The watch app uses the **phone's package name** (`studio.hypertext.logdate`, and
`studio.hypertext.logdate.debug` for debug builds) and is signed with the same key. It ships on the same
Play listing as a Wear OS form factor. This looks odd, but the Wear Data Layer requires it: notes and
audio only travel between a phone app and a watch app that match on both package name and signing key.

Two things follow from that:

- A debug phone build and a debug watch build made on the same machine share a package name and debug
  key, so they can talk to each other without any setup.
- A phone build signed with a different key (the local `dogfood` build type uses the upload key, Play
  uses the app signing key) will not exchange data with a watch build signed with another. Install
  both from Play, or both from the same machine.

The watch does not talk to the LogDate server. It hands notes and audio to the phone, and the phone
backs them up. Release identity, versioning and signing come from the `app.logdate.android-release`
plugin in `build-logic`; publishing is covered in
[Google Play Publishing](../../docs/reference/google-play-publishing.md#wear-os).

## Getting started

### Prerequisites

- Android Studio with Wear OS system images (API 33+ round)
- `adb` available on PATH ([setup](../../docs/environment/setup.md))
- A Wear OS emulator **or** a physical watch with developer options enabled

### Build and install

```bash
# Build debug APK
./gradlew :app:wear:assembleDebug

# Install on a connected Wear OS device or emulator
./gradlew :app:wear:installDebug
```

### Installing on a physical watch

1. **Enable developer options** on the watch:
   Settings > System > About > tap "Build number" 7 times.

2. **Enable ADB debugging**:
   Settings > Developer options > ADB debugging > ON.

3. **Connect over Wi-Fi** (watches rarely have USB ports):
   - On the watch: Settings > Developer options > Wireless debugging > ON.
     Note the IP address and port shown (e.g., `192.168.1.42:5555`).
   - On your machine:
     ```bash
     adb connect 192.168.1.42:5555
     ```
   - Accept the debugging prompt on the watch.

4. **Install**:
   ```bash
   ./gradlew :app:wear:installDebug
   ```
   Or install the APK directly:
   ```bash
   adb -s 192.168.1.42:5555 install app/wear/build/outputs/apk/debug/wear-debug.apk
   ```

5. **View logs**:
   ```bash
   adb -s 192.168.1.42:5555 logcat -s LogDate
   ```

> **Bluetooth debugging** (alternative): On watches without Wi-Fi debugging, pair through
> the Wear OS companion app on your phone, then enable Debug over Bluetooth in Developer
> options. See the [official guide][wear-debug-docs].

### Using the emulator

```bash
# Create a Wear OS AVD (round, API 34)
# Android Studio > Device Manager > Create Device > Wear OS > Small Round > API 34

# Or via command line
avdmanager create avd -n WearOS_Round -k "system-images;android-34;google_apis;x86_64" -d "wearos_small_round"
emulator -avd WearOS_Round
```

After the emulator boots:
```bash
./gradlew :app:wear:installDebug
```

> **Limitations**: Microphone input and Health Services require a physical watch. The
> emulator works for UI development, navigation, and screenshot tests.

## Features

### Implemented

| Feature | Description | Entry point |
|---------|-------------|-------------|
| **Voice recorder** | Tap to record until you tap again, or press and hold to talk and release to save. Pause, discard, and a five second Undo after saving | Home |
| **Voice memories** | Every recording newest first, with a player that has play and pause, 10 second skips and a progress bar | Home > headphones |
| **Mood Check-in** | Tap an emoji | Home > mood button |
| **Quick Text** | System speech-to-text, saved as a text note | Home > More > Quick text |
| **Timeline** | Entries by day, with inline audio playback | Home > More > Timeline |
| **Haptic Feedback** | Distinct vibration patterns for every interaction | Automatic |

### Implemented Platform Capabilities

- Timeline browser with day-by-day entry viewing
- Phone sync via the Wear Data Layer API
- Health Services integration with graceful fallback when unavailable
- Tiles and complications for quick capture and daily summaries
- Watch-owned location capture for standalone geotagged journal entries

## Architecture

```
app/wear/src/main/kotlin/app/logdate/wear/
├── LogDateWearApplication.kt        Koin setup (wearDataModule + wearAudioModule)
├── di/
│   ├── WearDataModule.kt            Room DB, repositories, DataStore
│   └── AudioModule.kt               ViewModels, recording infrastructure
├── haptic/
│   └── WearHapticEngine.kt          Centralized haptic patterns
├── recording/
│   ├── WearRecorder.kt              The recorder as screens see it
│   └── WearAudioRecordingManager.kt Session logic over the shared recording service
├── playback/
│   ├── WearVoiceNotePlayer.kt       Play, pause, skip, output checks
│   ├── WearPlaybackEngine.kt        Media3 playback behind a small interface
│   └── WearSyncedAudioResolver.kt   Fetches audio from the phone when the watch lacks it
├── location/
│   └── WearLocationCaptureCoordinator.kt  Journal-entry geotagging policy
├── data/storage/
│   └── StorageSpaceChecker.kt       Pre-recording space validation
├── presentation/
│   ├── MainActivity.kt              Single activity, hosts NavDisplay
│   ├── theme/Theme.kt               Material 3 for Wear OS
│   ├── navigation/WearNavRoutes.kt  NavKey route definitions
│   ├── home/                        The recorder screen + ViewModel
│   ├── recording/                   Recorder ViewModel and its state
│   ├── memories/                    Voice memories list and player
│   ├── more/                        Quick text, Timeline and Settings
│   ├── timeline/                    Day list and day detail
│   ├── mood/                        Emoji picker + ViewModel
│   └── quicktext/                   System STT handler
├── complication/
│   ├── EntryCountComplicationService.kt  Today's journal entry count
│   ├── MoodComplicationService.kt        Today's mood shortcut
│   ├── QuickCaptureComplicationService.kt Voice-note shortcut
│   └── StreakComplicationService.kt      Journaling streak
└── tile/
    ├── QuickCaptureTileService.kt   Voice, mood, and text capture shortcuts
    └── TodaySummaryTileService.kt   Daily summary and timeline shortcut
```

### Data layer

The watch runs the **same Room schema** as the phone (all entities, all migrations, SqlCipher
encryption). Most tables are simply empty on the watch. This eliminates sync impedance.

Key shared modules wired into the Wear app:
- `client/database` — Room DB + SqlCipher + DAOs
- `client/data` — `OfflineFirstJournalNotesRepository`
- `client/datastore` — User preferences
- `client/device` — `DatabasePassphraseProvider` (Android KeyStore)

### Dependency injection

Two Koin modules loaded in `LogDateWearApplication`:

- **`wearDataModule`** — Database, repositories, DataStore, passphrase provider
- **`wearAudioModule`** — ViewModels, `WearAudioRecordingManager`, the shared recording service controller, playback, `StorageSpaceChecker`, `WearHapticEngine`

### Navigation

Flat, single-level navigation from the home hub. All routes are defined as `NavKey` objects
in `WearNavRoutes.kt`. Back always returns home or exits. The app uses Navigation 3's
`NavDisplay` with `SwipeDismissableEntry` for swipe-to-dismiss.

## Permissions

Declared in `AndroidManifest.xml`:

| Permission | Purpose |
|------------|---------|
| `RECORD_AUDIO` | Microphone access for voice notes |
| `FOREGROUND_SERVICE` | Background recording |
| `FOREGROUND_SERVICE_MICROPHONE` | Foreground service type |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Playback that continues with the screen off |
| `POST_NOTIFICATIONS` | The recording and playback notifications |
| `WAKE_LOCK` | Keep recording when screen is off |
| `VIBRATE` | Haptic feedback |

## Testing

Three test layers cover every screen:

### Unit tests (`src/test/`)

```bash
./gradlew :app:wear:testDebugUnitTest
```

They cover the recorder session (`WearAudioRecordingManagerTest`), the recording ViewModel including
the tap and hold gesture, undo and interruptions (`WearRecordingViewModelTest`), the voice note player
and the memories screens' ViewModels, watch to phone sync and its acknowledgement handling, the mood and
settings ViewModels, and the haptic engine.

### Screenshot tests (`src/screenshotTest/`)

Render every screen state on small round and large round watch displays, on the watch's black
background. No device needed.

```bash
# Generate or update baseline images
./gradlew :app:wear:updateDebugScreenshotTest

# Validate current screenshots match baselines
./gradlew :app:wear:validateDebugScreenshotTest
```

Baselines live in `src/screenshotTestDebug/reference/` and are committed to git. Each preview
produces two images, one per display size. Home has a preview for every recorder state, and the
voice memories screens have previews for the list, the player, and its failure states.

The `@WearScreenshotPreviewMatrix` annotation applies both device specs automatically:
```kotlin
@Preview(name = "Small Round", device = "id:wearos_small_round")
@Preview(name = "Large Round", device = "id:wearos_large_round")
annotation class WearScreenshotPreviewMatrix
```

### Instrumented E2E tests (`src/androidTest/`)

Require a Wear OS emulator or a managed device.

```bash
./gradlew :app:wear:wearSmallRoundApi34DebugAndroidTest
```

`WearHomeScreenTest` checks the controls each recorder state shows, and the day detail, mood and sync
suites cover the rest. These tests render stateless content composables (such as `WearHomeContent`)
with controlled state, so they need neither a bound service nor a ViewModel.

## Quick reference

```bash
# Build
./gradlew :app:wear:assembleDebug

# Install on connected device
./gradlew :app:wear:installDebug

# Run unit tests
./gradlew :app:wear:testDebugUnitTest

# Generate screenshot baselines
./gradlew :app:wear:updateDebugScreenshotTest

# Validate screenshots
./gradlew :app:wear:validateDebugScreenshotTest

# Build the release bundle (see the Play publishing doc)
./gradlew :app:wear:bundleRelease

# Lint
./gradlew :app:wear:ktlintCheck
```

## Further reading

- [Audio recording feature](../../docs/feature-design/wear-audio-recording.md) — recording service, storage, battery
- [Testing strategy](../../docs/testing/introduction.md) — project-wide testing approach
- [Screenshot tests](../../docs/testing/screenshot-tests.md) — visual regression guide
- [Wear OS debugging][wear-debug-docs] — official setup docs

[wear-debug-docs]: https://developer.android.com/training/wearables/get-started/debugging
