package app.logdate.client.media.audio.transcription

import app.logdate.client.media.audio.download.ModelDownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Typed reason for a [TranscriptionResult.Error]. The UI maps each case to a
 * localized string; no raw exception messages should appear in the domain layer.
 */
sealed interface TranscriptionFailure {
    /** No network connection, or Data Saver blocking downloads on cellular. */
    data object NoNetwork : TranscriptionFailure

    /** Not enough device storage to install or load the transcription engine. */
    data object OutOfStorage : TranscriptionFailure

    /** Microphone or audio recording permission was denied. */
    data object PermissionDenied : TranscriptionFailure

    /** Speech recognition is not available on this device or platform. */
    data object NotAvailable : TranscriptionFailure

    /** This operation is not supported in the current configuration. */
    data object NotSupported : TranscriptionFailure

    /** An audio capture or processing error. */
    data object AudioError : TranscriptionFailure

    /** An unexpected error — check logs for the underlying cause. */
    data object Unknown : TranscriptionFailure
}

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
     * Transcription failed with a typed [reason]. Log the underlying cause
     * at the emission site; surfaces are not passed strings from this layer.
     */
    data class Error(
        val reason: TranscriptionFailure,
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
     * and the refinement step is skipped.
     */
    val isOfflineModelAvailable: Boolean
        get() = false

    /**
     * Live status of the offline model download. View models observe this
     * directly to render a download banner or progress indicator. Default
     * sits at [ModelDownloadStatus.NotSupported] for implementations that
     * don't ship a downloadable offline pass.
     */
    val offlineModelDownloadStatus: StateFlow<ModelDownloadStatus>
        get() = NotSupportedDownloadStatus

    /**
     * Idempotently kicks the offline model download. If a download is
     * already in flight or the model is already on disk, this is a no-op.
     * Progress flows out via [offlineModelDownloadStatus]. Default is a
     * no-op for implementations without a downloadable offline pass.
     */
    fun startOfflineModelDownload() = Unit

    /**
     * Releases resources when the service is no longer needed
     */
    fun release()
}

/**
 * Shared "no-op" download state used by [TranscriptionService] and
 * [app.logdate.client.media.audio.tagging.AudioTaggingService] implementations
 * that don't ship a downloadable model.
 */
private val NotSupportedDownloadStatus: StateFlow<ModelDownloadStatus> =
    MutableStateFlow(ModelDownloadStatus.NotSupported).asStateFlow()

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
