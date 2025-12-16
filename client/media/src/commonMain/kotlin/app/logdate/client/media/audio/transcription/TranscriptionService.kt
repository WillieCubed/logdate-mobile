package app.logdate.client.media.audio.transcription

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Result of a transcription request, including status and text if available
 */
sealed class TranscriptionResult {
    /**
     * Transcription is in progress
     */
    object InProgress : TranscriptionResult()

    /**
     * Transcription is complete with text result
     */
    data class Success(val text: String) : TranscriptionResult()

    /**
     * Transcription failed with error
     */
    data class Error(val message: String, val exception: Throwable? = null) : TranscriptionResult()
}

/**
 * Service interface for handling audio transcription
 */
interface TranscriptionService {
    /**
     * Subscribes to real-time transcription updates
     * @return Flow of transcription updates
     */
    fun getTranscriptionFlow(): SharedFlow<TranscriptionResult>

    /**
     * Starts a transcription session from live audio
     * @return true if started successfully
     */
    suspend fun startLiveTranscription(): Boolean

    /**
     * Stops a live transcription session
     */
    suspend fun stopLiveTranscription()

    /**
     * Transcribes a recorded audio file
     * @param audioUri URI to the audio file
     * @return TranscriptionResult with the final result
     */
    suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult

    /**
     * Cancels any in-progress transcription
     */
    fun cancelTranscription()

    /**
     * Gets the currently supported languages for transcription
     * @return List of language codes (e.g., "en-US", "fr-FR")
     */
    fun getSupportedLanguages(): List<String>

    /**
     * Sets the language for transcription
     * @param languageCode ISO language code (e.g., "en-US")
     */
    fun setLanguage(languageCode: String)

    /**
     * Whether this service supports live transcription
     */
    val supportsLiveTranscription: Boolean

    /**
     * Whether this service supports file transcription
     */
    val supportsFileTranscription: Boolean
    
    /**
     * Releases resources when the service is no longer needed
     */
    fun release()
}

/**
 * TranscriptionOptions for configuring the transcription service
 */
data class TranscriptionOptions(
    val language: String = "en-US",
    val enablePunctuation: Boolean = true,
    val enableInterimResults: Boolean = true,
    val enableWordTimestamps: Boolean = false,
    val modelQuality: ModelQuality = ModelQuality.STANDARD
)

/**
 * Quality levels for transcription models
 */
enum class ModelQuality {
    STANDARD,   // Standard quality, faster processing
    ENHANCED    // Enhanced quality, slower processing
}