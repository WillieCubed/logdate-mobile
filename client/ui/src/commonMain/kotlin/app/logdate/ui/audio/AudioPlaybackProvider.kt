package app.logdate.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * State for audio playback that is shared across the app.
 * Only one audio file can be playing at a time.
 */
data class AudioPlaybackState(
    val currentlyPlayingId: Uuid? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val duration: Duration = Duration.ZERO,
    val play: (id: Uuid, uri: String) -> Unit = { _, _ -> },
    val pause: () -> Unit = {},
    val stop: () -> Unit = {},
    val seekTo: (position: Float) -> Unit = {}
)

/**
 * CompositionLocal for accessing the audio playback state from anywhere in the app.
 */
val LocalAudioPlaybackState = compositionLocalOf { 
    AudioPlaybackState() 
}

/**
 * Provider composable that manages audio playback state and makes it available to all descendants.
 * This ensures only one audio can be played at a time throughout the app.
 */
@Composable
fun AudioPlaybackProvider(
    content: @Composable () -> Unit
) {
    // State that will be managed at this level
    var currentlyPlayingId by remember { mutableStateOf<Uuid?>(null) }
    var currentAudioUri by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableStateOf(Duration.ZERO) }
    
    // In a real implementation, you would initialize your platform-specific audio player here
    // For now, we'll just simulate the audio player with state
    
    // Functions to control playback
    val play: (id: Uuid, uri: String) -> Unit = { id, uri ->
        // Stop current playback if different ID
        if (currentlyPlayingId != id) {
            // In a real implementation, you would stop the current audio and load the new one
            progress = 0f
        }
        currentlyPlayingId = id
        currentAudioUri = uri
        isPlaying = true
        
        // In a real implementation, you would start the audio playback here
    }
    
    val pause: () -> Unit = {
        isPlaying = false
        // In a real implementation, you would pause the audio playback here
    }
    
    val stop: () -> Unit = {
        isPlaying = false
        currentlyPlayingId = null
        currentAudioUri = ""
        progress = 0f
        // In a real implementation, you would stop the audio playback here
    }
    
    val seekTo: (position: Float) -> Unit = { pos ->
        progress = pos
        // In a real implementation, you would seek to the position here
    }
    
    // Clean up when the composition is disposed
    DisposableEffect(Unit) {
        onDispose {
            // In a real implementation, you would release the audio player resources here
            stop()
        }
    }
    
    // Create state object with current values and functions
    val playbackState = AudioPlaybackState(
        currentlyPlayingId = currentlyPlayingId,
        isPlaying = isPlaying,
        progress = progress,
        duration = duration,
        play = play,
        pause = pause,
        stop = stop,
        seekTo = seekTo
    )
    
    // Provide the state to all descendants
    CompositionLocalProvider(LocalAudioPlaybackState provides playbackState) {
        content()
    }
}