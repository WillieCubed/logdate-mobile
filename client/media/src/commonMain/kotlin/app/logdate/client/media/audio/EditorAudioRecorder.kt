package app.logdate.client.media.audio

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Enhanced cross-platform audio recorder specifically for editor functionality.
 * Provides simple interface for recording, playback and visualization of audio
 * within the editor context.
 */
interface EditorAudioRecorder {
    /**
     * Current recording state
     */
    val recordingState: RecordingState
    
    /**
     * Start recording audio
     * @return True if recording started successfully
     */
    suspend fun startRecording(): Boolean
    
    /**
     * Pause current recording (if supported)
     * @return True if paused successfully
     */
    suspend fun pauseRecording(): Boolean
    
    /**
     * Resume paused recording (if supported)
     * @return True if resumed successfully
     */
    suspend fun resumeRecording(): Boolean
    
    /**
     * Stop and save the current recording
     * @return URI to the recorded audio file, or null if recording failed
     */
    suspend fun stopRecording(): String?
    
    /**
     * Cancel recording without saving
     */
    suspend fun cancelRecording()
    
    /**
     * Start playback of the recorded audio
     * @param uri URI of the audio file to play
     * @return True if playback started successfully
     */
    suspend fun startPlayback(uri: String): Boolean
    
    /**
     * Pause current playback
     * @return True if paused successfully
     */
    suspend fun pausePlayback(): Boolean
    
    /**
     * Resume paused playback
     * @return True if resumed successfully
     */
    suspend fun resumePlayback(): Boolean
    
    /**
     * Stop current playback
     */
    suspend fun stopPlayback()
    
    /**
     * Set playback volume
     * @param volume Volume level between 0.0 and 1.0
     */
    fun setVolume(volume: Float)
    
    /**
     * Seek to position in current playback
     * @param position Position in milliseconds
     */
    suspend fun seekTo(position: Duration)
    
    /**
     * Get real-time audio level during recording
     * @return Flow of audio level values between 0.0 and 1.0
     */
    fun getAudioLevelFlow(): Flow<Float>
    
    /**
     * Get current recording duration
     * @return Flow of duration values
     */
    fun getRecordingDurationFlow(): Flow<Duration>
    
    /**
     * Get current playback position
     * @return Flow of playback position values
     */
    fun getPlaybackPositionFlow(): Flow<Duration>
    
    /**
     * Get waveform data for recorded audio
     * @param uri URI of the audio file
     * @param samples Number of samples to generate
     * @return List of amplitude values between 0.0 and 1.0
     */
    suspend fun getWaveformData(uri: String, samples: Int = 100): List<Float>
    
    /**
     * Free resources when recorder is no longer needed
     */
    fun release()
}

/**
 * Enum representing the current recording state
 */
enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
    PLAYING,
    PLAYBACK_PAUSED
}

/**
 * Configuration options for the audio recorder
 */
data class EditorAudioRecorderConfig(
    val fileFormat: AudioFileFormat = AudioFileFormat.M4A,
    val sampleRate: Int = 44100,
    val bitRate: Int = 128000,
    val channels: Int = 2,
    val compressionQuality: Float = 0.8f
)

/**
 * Supported audio file formats
 */
enum class AudioFileFormat {
    M4A,
    MP3,
    WAV,
    AAC
}