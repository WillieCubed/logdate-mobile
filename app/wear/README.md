# `:app:wear` — Wear OS App

LogDate on your wrist. Record a thought with a tap, log your mood with an emoji, or hold the
screen and talk walkie-talkie style — your journal captures it all without pulling out your phone.

**Min SDK**: 31 (Wear OS 3+) | **Target SDK**: 35 | **Compile SDK**: 36

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
| **Walkie-Talkie** | Push-to-talk: hold the screen, speak, release to save | Home > Walkie-Talkie |
| **Voice Note** | Full recording studio with pause/resume and waveform | Home > Voice Note |
| **Mood Check-in** | Tap an emoji, optionally attach a voice note | Home > Mood Check-in |
| **Quick Text** | System speech-to-text, saved as a text note | Home > Quick Text |
| **Home Hub** | Greeting, entry count, capture chips, navigation | App launch |
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
│   ├── WearAudioRecordingService.kt Foreground service for mic recording
│   └── WearAudioRecordingManager.kt Manages MediaRecorder lifecycle
├── location/
│   └── WearLocationCaptureCoordinator.kt  Journal-entry geotagging policy
├── data/storage/
│   └── StorageSpaceChecker.kt       Pre-recording space validation
├── presentation/
│   ├── MainActivity.kt              Single activity, hosts NavDisplay
│   ├── theme/Theme.kt               Material 3 for Wear OS
│   ├── navigation/WearNavRoutes.kt  NavKey route definitions
│   ├── home/                        Hub screen + ViewModel
│   ├── walkietalkie/                Push-to-talk screen + ViewModel
│   ├── audio/                       Full recording screen + ViewModel + components
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
- **`wearAudioModule`** — ViewModels, `WearAudioRecordingManager`, `StorageSpaceChecker`, `WearHapticEngine`

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
| `WAKE_LOCK` | Keep recording when screen is off |
| `VIBRATE` | Haptic feedback |

## Testing

Three test layers cover every screen:

### Unit tests (`src/test/`)

```bash
./gradlew :app:wear:test
```

| File | Tests | Covers |
|------|-------|--------|
| `WalkieTalkieViewModelTest` | 15 | State machine, duration gate, auto-stop |
| `MoodCheckInViewModelTest` | 11 | Mood selection, note creation, voice attachment |
| `WearHapticEngineTest` | 16 | Pattern selection, preference-aware suppression |

### Screenshot tests (`src/screenshotTest/`)

Render every screen state on small round and large round watch displays. No device needed.

```bash
# Generate or update baseline images
./gradlew :app:wear:updateDebugScreenshotTest

# Validate current screenshots match baselines
./gradlew :app:wear:validateDebugScreenshotTest
```

Baselines live in `src/screenshotTestDebug/reference/` and are committed to git.

| File | Previews | States covered |
|------|----------|----------------|
| `WearHomeScreenshots` | 3 | Empty, populated, single entry |
| `WalkieTalkieScreenshots` | 8 | Ready, recording, long recording, saving, saved, too short, error, null error |
| `MoodCheckInScreenshots` | 5 | Emoji picker, voice prompt (great/sad/null), saved |
| `AudioRecordingScreenshots` | 4 | Idle, active, paused, error |

Each preview generates 2 PNGs (small + large round) = **40 baseline images** total.

The `@WearScreenshotPreviewMatrix` annotation applies both device specs automatically:
```kotlin
@Preview(name = "Small Round", device = "id:wearos_small_round")
@Preview(name = "Large Round", device = "id:wearos_large_round")
annotation class WearScreenshotPreviewMatrix
```

### Instrumented E2E tests (`src/androidTest/`)

Require a connected Wear OS device or emulator.

```bash
./gradlew :app:wear:connectedAndroidTest
```

| File | Tests | Covers |
|------|-------|--------|
| `WearHomeScreenTest` | 12 | Greeting, entry count, all chips, navigation callbacks |
| `WalkieTalkieScreenTest` | 14 | All screen states, duration formatting |
| `MoodCheckInScreenTest` | 11 | Mood selection, voice prompt, saved confirmation |
| `AudioRecordingScreenTest` | 8 | Idle, recording, paused, error, button callbacks |

These tests render stateless content composables (`WearHomeContent`, `ReadyContent`, etc.)
with controlled state, avoiding the need for bound services or ViewModels.

### Test totals

| Layer | Tests | Requires device |
|-------|-------|-----------------|
| Unit | 42 | No |
| Screenshot | 40 baselines | No |
| E2E instrumented | 45 | Yes |

## Quick reference

```bash
# Build
./gradlew :app:wear:assembleDebug

# Install on connected device
./gradlew :app:wear:installDebug

# Run unit tests
./gradlew :app:wear:test

# Generate screenshot baselines
./gradlew :app:wear:updateDebugScreenshotTest

# Validate screenshots
./gradlew :app:wear:validateDebugScreenshotTest

# Run E2E tests on device
./gradlew :app:wear:connectedAndroidTest

# Lint
./gradlew :app:wear:ktlintCheck
```

## Further reading

- [Audio recording feature](../../docs/feature-design/wear-audio-recording.md) — recording service, storage, battery
- [Testing strategy](../../docs/testing/introduction.md) — project-wide testing approach
- [Screenshot tests](../../docs/testing/screenshot-tests.md) — visual regression guide
- [Wear OS debugging][wear-debug-docs] — official setup docs

[wear-debug-docs]: https://developer.android.com/training/wearables/get-started/debugging
