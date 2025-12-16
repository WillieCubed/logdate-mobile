package app.logdate.client.media.audio

import app.logdate.client.media.audio.transcription.TranscriptionService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
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
     * Sets the transcription service to use for speech recognition
     */
    fun setTranscriptionService(service: TranscriptionService)
    
    /**
     * Releases resources when recording is no longer needed
     */
    fun release()
    
    /**
     * Whether recording is currently in progress
     */
    val isRecording: Boolean
}