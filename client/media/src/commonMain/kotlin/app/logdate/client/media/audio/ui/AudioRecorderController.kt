package app.logdate.client.media.audio.ui

import app.logdate.client.media.audio.EditorAudioRecorder
import app.logdate.client.media.audio.RecordingState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration

/**
 * AudioRecorderController provides a simple interface to control audio recording and playback
 * for the editor feature.
 */
class AudioRecorderController(
    private val scope: CoroutineScope
) : KoinComponent {
    // Inject the platform-specific implementation
    private val recorder: EditorAudioRecorder by inject()
    
    /**
     * Get the current recording state
     */
    val recordingState: RecordingState
        get() = recorder.recordingState
    
    /**
     * Get flow of audio levels for visualization (values between 0.0 and 1.0)
     */
    fun getAudioLevels(): Flow<Float> = recorder.getAudioLevelFlow()
    
    /**
     * Get flow of recording duration
     */
    fun getRecordingDuration(): Flow<Duration> = recorder.getRecordingDurationFlow()
    
    /**
     * Get flow of playback position
     */
    fun getPlaybackPosition(): Flow<Duration> = recorder.getPlaybackPositionFlow()
    
    /**
     * Start recording audio
     * @param onSuccess Callback when recording starts successfully
     * @param onError Callback when recording fails to start
     */
    fun startRecording(onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        scope.launch {
            val success = recorder.startRecording()
            if (success) {
                onSuccess()
            } else {
                Napier.e("Failed to start recording")
                onError()
            }
        }
    }
    
    /**
     * Pause recording if supported
     * @param onSuccess Callback when recording pauses successfully
     * @param onError Callback when pausing fails
     */
    fun pauseRecording(onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        scope.launch {
            val success = recorder.pauseRecording()
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }
    
    /**
     * Resume paused recording if supported
     * @param onSuccess Callback when recording resumes successfully
     * @param onError Callback when resuming fails
     */
    fun resumeRecording(onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        scope.launch {
            val success = recorder.resumeRecording()
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }
    
    /**
     * Stop and save the recording
     * @param onSuccess Callback with the URI of the saved recording
     * @param onError Callback when stopping fails
     */
    fun stopRecording(onSuccess: (String) -> Unit = {}, onError: () -> Unit = {}) {
        scope.launch {
            val uri = recorder.stopRecording()
            if (uri != null) {
                onSuccess(uri)
            } else {
                onError()
            }
        }
    }
    
    /**
     * Cancel recording without saving
     * @param onComplete Callback when cancellation is complete
     */
    fun cancelRecording(onComplete: () -> Unit = {}) {
        scope.launch {
            recorder.cancelRecording()
            onComplete()
        }
    }
    
    /**
     * Start playback of a recording
     * @param uri URI of the audio file to play
     * @param onSuccess Callback when playback starts successfully
     * @param onError Callback when starting playback fails
     */
    fun startPlayback(uri: String, onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        scope.launch {
            val success = recorder.startPlayback(uri)
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }
    
    /**
     * Pause current playback
     * @param onSuccess Callback when playback pauses successfully
     * @param onError Callback when pausing fails
     */
    fun pausePlayback(onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        scope.launch {
            val success = recorder.pausePlayback()
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }
    
    /**
     * Resume paused playback
     * @param onSuccess Callback when playback resumes successfully
     * @param onError Callback when resuming fails
     */
    fun resumePlayback(onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        scope.launch {
            val success = recorder.resumePlayback()
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }
    
    /**
     * Stop current playback
     * @param onComplete Callback when stopping is complete
     */
    fun stopPlayback(onComplete: () -> Unit = {}) {
        scope.launch {
            recorder.stopPlayback()
            onComplete()
        }
    }
    
    /**
     * Seek to position in playback
     * @param position Position to seek to
     */
    fun seekTo(position: Duration) {
        scope.launch {
            recorder.seekTo(position)
        }
    }
    
    /**
     * Set playback volume
     * @param volume Volume level between 0.0 and 1.0
     */
    fun setVolume(volume: Float) {
        recorder.setVolume(volume)
    }
    
    /**
     * Get waveform data for visualization
     * @param uri URI of the audio file
     * @param samples Number of samples to generate
     * @param onComplete Callback with the waveform data
     */
    fun getWaveformData(uri: String, samples: Int = 100, onComplete: (List<Float>) -> Unit) {
        scope.launch {
            val data = recorder.getWaveformData(uri, samples)
            onComplete(data)
        }
    }
    
    /**
     * Release resources when no longer needed
     * Should be called when the feature is no longer active
     */
    fun release() {
        recorder.release()
    }
}