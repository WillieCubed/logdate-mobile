package app.logdate.client.media.audio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration

/**
 * Composable state container for audio recording functionality.
 * Manages recording state, audio levels, and recording duration.
 */
@Composable
fun rememberAudioRecordingState(
    initialIsRecording: Boolean = false,
    initialIsPaused: Boolean = false,
    initialDuration: Duration = Duration.ZERO,
    initialAudioLevels: List<Float> = emptyList(),
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onPauseRecording: () -> Unit = {},
    onAudioLevelUpdate: (Float) -> Unit = {},
): AudioRecordingState {
    return remember {
        AudioRecordingState(
            initialIsRecording = initialIsRecording,
            initialIsPaused = initialIsPaused,
            initialDuration = initialDuration,
            initialAudioLevels = initialAudioLevels,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onPauseRecording = onPauseRecording,
            onAudioLevelUpdate = onAudioLevelUpdate
        )
    }
}

/**
 * State holder for audio recording functionality.
 */
open class AudioRecordingState(
    initialIsRecording: Boolean = false,
    initialIsPaused: Boolean = false,
    initialDuration: Duration = Duration.ZERO,
    initialAudioLevels: List<Float> = emptyList(),
    protected val onStartRecording: () -> Unit = {},
    protected val onStopRecording: () -> Unit = {},
    protected val onPauseRecording: () -> Unit = {},
    private val onAudioLevelUpdate: (Float) -> Unit = {},
) {
    var isRecording by mutableStateOf(initialIsRecording)
        private set
    
    var isPaused by mutableStateOf(initialIsPaused)
        private set
    
    var duration by mutableStateOf(initialDuration)
        private set
    
    var audioLevels by mutableStateOf(initialAudioLevels)
        private set
    
    var transcription by mutableStateOf<String?>(null)
        private set
    
    /**
     * Starts audio recording
     */
    open fun startRecording() {
        if (!isRecording) {
            isRecording = true
            isPaused = false
            onStartRecording()
        } else if (isPaused) {
            isPaused = false
            onStartRecording()
        }
    }
    
    /**
     * Pauses audio recording
     */
    open fun pauseRecording() {
        if (isRecording && !isPaused) {
            isPaused = true
            onPauseRecording()
        }
    }
    
    /**
     * Stops audio recording
     */
    open fun stopRecording() {
        if (isRecording || isPaused) {
            isRecording = false
            isPaused = false
            onStopRecording()
        }
    }
    
    /**
     * Updates the recording duration
     */
    fun updateDuration(newDuration: Duration) {
        duration = newDuration
    }
    
    /**
     * Adds a new audio level to the waveform
     */
    fun addAudioLevel(level: Float, maxHistory: Int = 50) {
        val normalizedLevel = level.coerceIn(0f, 1f)
        audioLevels = (audioLevels + normalizedLevel).takeLast(maxHistory)
        onAudioLevelUpdate(level)
    }
    
    /**
     * Updates the live transcription text
     */
    fun updateTranscription(text: String?) {
        transcription = text
    }
    
    /**
     * Clears all recording data
     */
    fun clear() {
        duration = Duration.ZERO
        audioLevels = emptyList()
        transcription = null
    }
    
    /**
     * Resets the state to initial values
     */
    fun reset() {
        isRecording = false
        isPaused = false
        clear()
    }
}