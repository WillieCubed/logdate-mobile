# Audio Playback Provider

This module provides a global audio playback state management system for the LogDate app using Jetpack Compose's CompositionLocal API.

## Purpose

The `AudioPlaybackProvider` ensures that only one audio note can be played at a time throughout the app, regardless of which screen or component initiates the playback. This follows a singleton pattern for audio playback across the entire application.

## Components

### AudioPlaybackState

This data class contains all the necessary state and functions for audio playback:

- `currentlyPlayingId`: The ID of the currently playing audio note (if any)
- `isPlaying`: Whether audio is currently playing
- `progress`: Current playback progress (0-1)
- `duration`: Total duration of the current audio
- Functions for controlling playback: `play()`, `pause()`, `stop()`, `seekTo()`

### LocalAudioPlaybackState

A CompositionLocal that provides access to the `AudioPlaybackState` to all descendant composables.

### AudioPlaybackProvider

A composable that wraps content and provides the `AudioPlaybackState` to all descendants. This should be placed high in the composition hierarchy, typically at the app root or main navigation container level.

## Usage

1. Wrap your app content with the `AudioPlaybackProvider`:

```kotlin
@Composable
fun MyApp() {
    AudioPlaybackProvider {
        // Your app content here
    }
}
```

2. Access the playback state in any descendant composable:

```kotlin
@Composable
fun AudioPlayerComponent(audioId: Uuid, audioUri: String) {
    val playbackState = LocalAudioPlaybackState.current
    val isThisPlaying = playbackState.currentlyPlayingId == audioId && playbackState.isPlaying
    
    IconButton(
        onClick = { 
            if (isThisPlaying) {
                playbackState.pause()
            } else {
                playbackState.play(audioId, audioUri)
            }
        }
    ) {
        Icon(
            imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isThisPlaying) "Pause" else "Play"
        )
    }
}
```

## Integration Notes

- The provider only manages the playback state - you'll need to implement the actual audio playback functionality based on your platform.
- Use platform-specific code (expect/actual pattern) to implement the actual audio playback in the `AudioPlaybackProvider`.
- The current implementation simulates audio playback for demonstration purposes.

## Best Practices

1. Place the `AudioPlaybackProvider` as high in the composition hierarchy as makes sense (typically at the app root).
2. Don't create multiple instances of `AudioPlaybackProvider` as it would defeat the purpose of having a global singleton.
3. Keep the provider lightweight - it should only manage state, not perform heavy computations.
4. Use the `remember` API with the state from `LocalAudioPlaybackState.current` to minimize recompositions.