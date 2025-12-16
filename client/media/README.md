# `:client:media`

**Media handling and processing functionality**

## Overview

Provides comprehensive media handling capabilities including image, video, and audio capture, processing, and management across all platforms. This module serves as the foundation for all media-related functionality in the application.

## Architecture

```
Media Module
├── Core Media Management
├── Audio Recording & Processing
├── Image Handling
└── Platform-Specific Implementations
```

## Key Components

### Core Components

- `MediaManager.kt` - Central media access interface
- `MediaObject.kt` - Media representation models
- `MediaModule.kt` - Dependency injection

### Audio Features

- `AudioRecordingManager.kt` - Audio recording interface
- `EditorAudioRecorder.kt` - Journal entry audio recording
- `AudioRecorderController.kt` - UI controller for audio recording
- `TranscriptionService.kt` - Speech-to-text functionality

### Platform-Specific

- Android implementation with MediaStore
- iOS implementation with AVFoundation
- Desktop implementation with platform APIs

## Features

### Media Management

- **Media Access**: Unified access to device media
- **Media Retrieval**: Query media by type and date
- **Media Collections**: Organize media into collections
- **Recent Media**: Access recently added media
- **Media Metadata**: Extract and process media information

### Audio Recording

- **Voice Notes**: High-quality audio recording
- **Recording States**: Managed recording lifecycle
- **Audio Levels**: Real-time audio level monitoring
- **Duration Tracking**: Recording time tracking
- **Transcription**: Speech-to-text conversion

### Image Handling

- **Image Access**: Retrieve device images
- **Image Storage**: Store application images
- **Image Metadata**: Extract image information
- **Image Collections**: Organize images

### Video Support

- **Video Access**: Retrieve device videos
- **Video Metadata**: Extract video information
- **Duration Handling**: Video duration information
- **Video Collections**: Organize videos

## Dependencies

### Core Dependencies

- **Kotlinx Coroutines**: Asynchronous operations
- **Kotlinx DateTime**: Date and time handling
- **Kotlinx Serialization**: Serialization support
- **Compose Runtime**: Reactive programming
- **Material 3**: UI components
- **Koin**: Dependency injection
- **Napier**: Logging

## Usage Patterns

### Media Access

```kotlin
class MediaRepository(private val mediaManager: MediaManager) {
    suspend fun getRecentMedia(): Flow<List<MediaObject>> {
        return mediaManager.getRecentMedia()
    }
    
    suspend fun getMediaForDate(date: LocalDate): Flow<List<MediaObject>> {
        val start = date.atStartOfDay().toInstant(TimeZone.currentSystemDefault())
        val end = date.plus(1, DateTimeUnit.DAY)
            .atStartOfDay().toInstant(TimeZone.currentSystemDefault())
        return mediaManager.queryMediaByDate(start, end)
    }
}
```

### Audio Recording

```kotlin
class AudioNoteViewModel(private val audioRecordingManager: AudioRecordingManager) {
    private val _recordingState = MutableStateFlow(AudioRecordingState())
    val recordingState: StateFlow<AudioRecordingState> = _recordingState
    
    fun startRecording() {
        viewModelScope.launch {
            val success = audioRecordingManager.startRecording()
            if (success) {
                collectAudioLevels()
                collectDuration()
            }
        }
    }
    
    fun stopRecording() {
        viewModelScope.launch {
            val fileUri = audioRecordingManager.stopRecording()
            // Process the recorded file
        }
    }
    
    private fun collectAudioLevels() {
        viewModelScope.launch {
            audioRecordingManager.getAudioLevelFlow().collect { level ->
                _recordingState.update { it.copy(audioLevel = level) }
            }
        }
    }
    
    private fun collectDuration() {
        viewModelScope.launch {
            audioRecordingManager.getRecordingDurationFlow().collect { duration ->
                _recordingState.update { it.copy(duration = duration) }
            }
        }
    }
}
```

## Dependency Injection

```kotlin
val mediaModule = module {
    single<MediaManager> { PlatformMediaManager(get()) }
    single<AudioRecordingManager> { PlatformAudioRecordingManager(get()) }
    factory { EditorAudioRecorder(get()) }
}

val audioModule = module {
    factory<TranscriptionService> { PlatformTranscriptionService() }
}
```

## TODOs

### Core Features
- [ ] Add media file caching system
- [ ] Implement media preprocessing pipeline
- [ ] Add support for media compression
- [ ] Implement media backup integration
- [ ] Add media syncing capabilities

### Audio Features
- [ ] Improve audio quality options
- [ ] Add audio filtering capabilities
- [ ] Implement background noise reduction
- [ ] Add audio visualization components
- [ ] Improve transcription accuracy

### Image Features
- [ ] Add image editing capabilities
- [ ] Implement image optimization
- [ ] Add image metadata extraction
- [ ] Implement image categorization
- [ ] Add image content analysis

### Video Features
- [ ] Add video capture support
- [ ] Implement video trimming
- [ ] Add video thumbnail generation
- [ ] Implement video playback controls
- [ ] Add video metadata extraction