package app.logdate.feature.editor.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aakira.napier.Napier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Composable state container for audio playback functionality.
 * Manages playback state, progress, and audio file information.
 * 
 * This state holder is used to maintain the UI state for the audio playback component
 * and coordinate with the AudioViewModel for actual audio playback.
 */
@Composable
fun rememberAudioPlaybackState(
    audioUri: String? = null,
    initialIsPlaying: Boolean = false,
    initialProgress: Duration = Duration.ZERO,
    initialTotalDuration: Duration = Duration.ZERO,
    onStartPlayback: () -> Unit = {},
    onStopPlayback: () -> Unit = {},
    onSeek: (Duration, Duration) -> Unit = { _, _ -> },
): AudioPlaybackState {
    return remember(audioUri) {
        AudioPlaybackState(
            audioUri = audioUri,
            initialIsPlaying = initialIsPlaying,
            initialProgress = initialProgress,
            initialTotalDuration = initialTotalDuration,
            onStartPlayback = onStartPlayback,
            onStopPlayback = onStopPlayback,
            onSeek = onSeek
        )
    }
}

/**
 * State holder for audio playback functionality.
 */
class AudioPlaybackState(
    val audioUri: String? = null,
    initialIsPlaying: Boolean = false,
    initialProgress: Duration = Duration.ZERO,
    initialTotalDuration: Duration = Duration.ZERO,
    private val onStartPlayback: () -> Unit = {},
    private val onStopPlayback: () -> Unit = {},
    private val onSeek: (Duration, Duration) -> Unit = { _, _ -> },
) {
    var isPlaying by mutableStateOf(initialIsPlaying)
        private set
    
    var progress by mutableStateOf(initialProgress)
        private set
    
    var totalDuration by mutableStateOf(initialTotalDuration)
        private set
    
    var isLoading by mutableStateOf(false)
        internal set
    
    var hasError by mutableStateOf(false)
        private set
    
    /**
     * Whether this playback state has audio available
     */
    val hasAudio: Boolean
        get() = audioUri != null
    
    /**
     * Playback progress as a percentage (0.0 to 1.0)
     */
    val progressPercentage: Float
        get() = if (totalDuration > Duration.ZERO) {
            (progress.inWholeMilliseconds.toFloat() / totalDuration.inWholeMilliseconds.toFloat())
                .coerceIn(0f, 1f)
        } else {
            0f
        }
    
    /**
     * Starts audio playback
     */
    fun startPlayback() {
        if (hasAudio && !isPlaying && !isLoading) {
            isPlaying = true
            hasError = false
            try {
                onStartPlayback()
            } catch (e: Exception) {
                setError(true)
            }
        }
    }
    
    /**
     * Stops/pauses audio playback
     */
    fun stopPlayback() {
        if (isPlaying) {
            isPlaying = false
            try {
                onStopPlayback()
            } catch (e: Exception) {
                setError(true)
            }
        }
    }
    
    /**
     * Toggles playback state
     */
    fun togglePlayback() {
        if (isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }
    
    /**
     * Seeks to a specific position in the audio
     */
    fun seekTo(position: Duration) {
        if (hasAudio && position >= Duration.ZERO && position <= totalDuration) {
            progress = position
            try {
                onSeek(position, totalDuration)
            } catch (e: Exception) {
                setError(true)
            }
        }
    }
    
    /**
     * Seeks to a percentage of the total duration
     */
    fun seekToPercentage(percentage: Float) {
        val clampedPercentage = percentage.coerceIn(0f, 1f)
        val targetMillis = (totalDuration.inWholeMilliseconds * clampedPercentage).toLong()
        val targetPosition = targetMillis.milliseconds
        seekTo(targetPosition)
    }
    
    /**
     * Updates the current playback progress
     */
    fun updateProgress(newProgress: Duration) {
        progress = newProgress.coerceIn(Duration.ZERO, totalDuration)
    }
    
    /**
     * Updates the total duration of the audio
     */
    fun updateTotalDuration(duration: Duration) {
        totalDuration = duration
    }
    
    /**
     * Sets the loading state
     */
    fun updateLoadingState(loading: Boolean) {
        isLoading = loading
    }
    
    /**
     * Updates the playback state
     */
    fun updatePlaybackState(playing: Boolean) {
        isPlaying = playing
    }
    
    /**
     * Sets the error state
     */
    fun setError(error: Boolean) {
        hasError = error
        if (error) {
            isPlaying = false
            isLoading = false
        }
    }
    
    /**
     * Resets the playback state
     */
    fun reset() {
        isPlaying = false
        progress = Duration.ZERO
        isLoading = false
        hasError = false
    }
}