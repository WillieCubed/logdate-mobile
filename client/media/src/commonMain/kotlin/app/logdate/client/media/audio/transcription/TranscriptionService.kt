package app.logdate.client.media.audio.transcription

import app.logdate.client.media.audio.download.ModelDownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Result of a transcription request, including status and text if available
 */
sealed class TranscriptionResult {
    /**
     * Transcription is in progress
     */
    object InProgress : TranscriptionResult()

    /**
     * Transcription is complete with text result.
     *
     * @param text the transcript so far
     * @param isFinal true once no further updates are expected for this recording
     * @param isRefining true while a higher-accuracy pass is still rewriting parts
     *   of the transcript in the background. The UI should accept that [text]
     *   may continue to change visibly even after [isFinal] is true.
     */
    data class Success(
        val text: String,
        val timedTranscript: TimedTranscript? = null,
        val isFinal: Boolean = false,
        val isRefining: Boolean = false,
    ) : TranscriptionResult()

    /**
     * Transcription failed with error
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null,
    ) : TranscriptionResult()
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
     * Resets accumulated transcription text and restarts recognition if active.
     * Used when the user wants to start a fresh recording.
     */
    suspend fun resetTranscription()

    /**
     * Pre-loads models so that subsequent calls to [startLiveTranscription] start faster.
     * Implementations should make this idempotent. The default is a no-op.
     */
    suspend fun warmUp() {}

    /**
     * Whether the higher-accuracy offline model (Whisper, etc.) is present on
     * device. When false, transcription falls back to the streaming pass only
     * and the refinement step is skipped. The UI can use this to decide
     * whether to prompt for the model download.
     *
     * Default is `false`; implementations that don't have an offline pass at
     * all leave this alone and never need to expose the download flow.
     */
    val isOfflineModelAvailable: Boolean
        get() = false

    /**
     * Downloads the offline model used for the refinement pass and emits
     * [ModelDownloadStatus] updates as it progresses. The flow runs to
     * [ModelDownloadStatus.Completed] on success or [ModelDownloadStatus.Failed]
     * on error. Default emits [ModelDownloadStatus.Failed] for implementations
     * that don't support an offline model.
     */
    fun downloadOfflineModel(): Flow<ModelDownloadStatus> = flowOf(ModelDownloadStatus.NotSupported)

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
    val modelQuality: ModelQuality = ModelQuality.STANDARD,
)

/**
 * Quality levels for transcription models
 */
enum class ModelQuality {
    STANDARD, // Standard quality, faster processing
    ENHANCED, // Enhanced quality, slower processing
}
