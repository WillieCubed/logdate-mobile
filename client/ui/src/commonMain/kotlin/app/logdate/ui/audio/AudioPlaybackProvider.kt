package app.logdate.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackStatus
import app.logdate.client.media.audio.AudioPlaybackStatusProvider
import org.koin.compose.koinInject
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
    val audioPlaybackManager: AudioPlaybackManager = koinInject()
    val statusProvider = audioPlaybackManager as? AudioPlaybackStatusProvider

    // State that will be managed at this level
    var currentlyPlayingId by remember { mutableStateOf<Uuid?>(null) }
    var currentAudioUri by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableStateOf(Duration.ZERO) }
    val playbackStatus = statusProvider?.playbackStatus?.collectAsState(
        initial = AudioPlaybackStatus()
    )

    LaunchedEffect(playbackStatus?.value) {
        val statusValue = playbackStatus?.value ?: return@LaunchedEffect
        isPlaying = statusValue.isPlaying
        progress = statusValue.progress
        duration = statusValue.duration
    }
    
    // Functions to control playback
    val play: (id: Uuid, uri: String) -> Unit = { id, uri ->
        // Stop current playback if different ID
        if (currentlyPlayingId != id) {
            audioPlaybackManager.stopPlayback()
            progress = 0f
        }
        currentlyPlayingId = id
        currentAudioUri = uri
        isPlaying = true

        audioPlaybackManager.startPlayback(
            uri = uri,
            metadata = AudioPlaybackMetadata(noteId = id),
            onProgressUpdated = { newProgress ->
                progress = newProgress
            },
            onPlaybackCompleted = {
                isPlaying = false
                progress = 1f
            }
        )
    }
    
    val pause: () -> Unit = {
        isPlaying = false
        audioPlaybackManager.pausePlayback()
    }
    
    val stop: () -> Unit = {
        isPlaying = false
        currentlyPlayingId = null
        currentAudioUri = null
        progress = 0f
        audioPlaybackManager.stopPlayback()
    }
    
    val seekTo: (position: Float) -> Unit = { pos ->
        progress = pos
        audioPlaybackManager.seekTo(pos)
    }
    
    // Clean up when the composition is disposed
    DisposableEffect(audioPlaybackManager) {
        onDispose {
            audioPlaybackManager.release()
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
