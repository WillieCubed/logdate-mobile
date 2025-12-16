# Wear OS Audio Recording Feature

## Overview

The audio recording feature for LogDate's Wear OS app allows users to capture audio notes directly from their watch. It's optimized for the wearable form factor with:

- Simple, glanceable UI with large touch targets
- Storage space validation to prevent failed recordings
- Battery-efficient implementation
- Haptic feedback for interaction confirmation
- Background recording capability with foreground service

## Architecture

The implementation follows clean architecture principles and reuses core components from the mobile app:

```
Wear App
├── UI Layer
│   ├── AudioRecordingScreen (Composable)
│   ├── Components (RecordButton, AudioWaveform, etc.)
│   └── AudioViewModel (Reused from main app)
├── Domain Layer
│   └── WearJournalNotesRepository
├── Data Layer
│   ├── WearAudioRecordingService
│   ├── WearAudioRecordingManager (implements AudioRecordingManager)
│   └── StorageSpaceChecker
└── DI
    └── AudioModule
```

## Shared Components

This implementation maximizes code reuse by sharing components with the mobile app:

- **AudioViewModel**: Reused directly from the mobile app
- **AudioRecordingManager**: Interface shared, with Wear OS-specific implementation
- **AudioUiState**: Shared UI state representation
- **Repository interfaces**: Compatible with the mobile app

For components not fully implemented on Wear OS (playback, transcription), we provide stub implementations that satisfy the interface requirements.

## Key Components

### UI Components

- **AudioRecordingScreen**: Wear OS-optimized Composable UI
- **RecordButton**: Large, touch-friendly recording control
- **AudioWaveform**: Simplified waveform visualization
- **RecordingTimer**: Displays elapsed recording time

### Audio Recording

- **WearAudioRecordingManager**: Implementation of the shared `AudioRecordingManager` interface
- **WearAudioRecordingService**: Foreground service that handles recording in the background
- **StorageSpaceChecker**: Utility to verify available storage space before recording

### Stub Implementations

- **StubAudioPlaybackManager**: No-op implementation of the AudioPlaybackManager interface
- **WearStubTranscriptionRepository**: Stub implementation of the TranscriptionRepository

## Storage Space Validation

One key requirement is preventing recording failures due to insufficient storage:

1. Before starting a recording, the system checks if there's enough space for a 1-minute recording
2. The estimate is based on a 128kbps AAC audio format (approximately 960KB per minute)
3. A buffer of 512KB is added to ensure safe operation
4. If insufficient space is available, the recording is blocked and the user is notified

## Haptic Feedback

Tactile feedback is crucial for the watch experience. The service provides distinct haptic patterns:

- Start Recording: Strong single vibration
- Stop Recording: Double click pattern
- Pause Recording: Light tick
- Resume Recording: Light double tick

## Battery Optimization

To minimize battery impact on the wearable device:

1. Audio is recorded at 128kbps AAC (good quality/size balance)
2. UI updates are throttled to 100ms intervals
3. Wake lock ensures recording continues when the screen is off
4. Minimal UI animations reduce power consumption

## Integration with Mobile App

The Wear OS recordings are stored locally on the watch and can be synced with the mobile app through the Data Layer API (not implemented in this version). The data model is compatible with the phone app's model.

## Permissions

The feature requires these permissions:
- `RECORD_AUDIO`: For microphone access
- `FOREGROUND_SERVICE`: For background recording
- `FOREGROUND_SERVICE_MICROPHONE`: For foreground service microphone access
- `WAKE_LOCK`: To keep recording when screen is off
- `VIBRATE`: For haptic feedback

## Usage

1. User taps "Record Audio" button from the main screen
2. The recording screen appears with a large red record button
3. Tapping the record button starts recording with haptic feedback
4. During recording:
   - Audio waveform visualizes sound levels
   - Timer shows elapsed time
   - Pause/Resume button allows temporarily stopping
   - Cancel button abandons the recording
5. Tapping the stop button (previously the record button) ends recording
6. The recording is saved as a JournalNote.Audio and the user returns to the previous screen

## Storage Management

Audio files follow this lifecycle:
1. Initially stored in the cache directory during recording
2. On successful save, moved to a permanent location in the app's files directory
3. Named using the note's UUID for easy retrieval
4. Can be deleted when the associated note is removed

## Code Reuse Benefits

Reusing the ViewModel and other components from the mobile app provides several benefits:

1. Consistent behavior across platforms
2. Reduced development and maintenance effort
3. Shared bug fixes and improvements
4. Easier testing due to common code base
5. Simplified future enhancements

## Implementation Notes

- Default recording length is not limited, but estimated storage check is based on 1 minute
- UI is optimized for both round and square watch faces
- Swipe-to-dismiss gesture is supported for cancelling recording
- Error handling provides clear user feedback
- Uses Wear OS Material 3 components for consistent design

## Future Enhancements

Potential improvements for future versions:

1. Sync with phone app via Data Layer API
2. Voice-to-text transcription
3. Voice commands for hands-free operation
4. Recording quality settings
5. Audio note sharing
6. Smart suggestions based on recording content
7. Integration with Wear Tiles for quick access