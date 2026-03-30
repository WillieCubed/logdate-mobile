package app.logdate.client.media.audio

import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration

/**
 * Interface for audio recording functionality across platforms
 */
interface AudioRecordingManager {
    /**
     * Starts audio recording
     * @return True if recording started successfully
     */
    suspend fun startRecording(): Boolean

    /**
     * Stops audio recording
     * @return URI to the recorded audio file, or null if recording failed
     */
    suspend fun stopRecording(): String?

    /**
     * Gets a flow of audio level values during recording (0.0 to 1.0)
     */
    fun getAudioLevelFlow(): Flow<Float>

    /**
     * Gets a flow of time elapsed during recording
     */
    fun getRecordingDurationFlow(): Flow<Duration>

    /**
     * Gets a flow of transcription text if available
     */
    fun getTranscriptionFlow(): Flow<String?>

    /**
     * Gets structured transcription updates when timing metadata is available.
     */
    fun getStructuredTranscriptionFlow(): Flow<TranscriptionResult> = emptyFlow()

    /**
     * Sets the transcription service to use for speech recognition
     */
    fun setTranscriptionService(service: TranscriptionService)

    /**
     * Resets accumulated transcription text. Used when restarting a recording.
     * Default implementation is a no-op.
     */
    suspend fun resetTranscription() {}

    /**
     * Releases resources when recording is no longer needed
     */
    fun release()

    /**
     * Pauses the current recording if supported by the platform.
     * Default implementation does nothing and returns false.
     *
     * @return True if recording was successfully paused.
     */
    suspend fun pauseRecording(): Boolean = false

    /**
     * Resumes a paused recording if supported by the platform.
     * Default implementation does nothing and returns false.
     *
     * @return True if recording was successfully resumed.
     */
    suspend fun resumeRecording(): Boolean = false

    /**
     * Whether recording is currently in progress
     */
    val isRecording: Boolean
}
