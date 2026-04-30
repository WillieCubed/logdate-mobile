package app.logdate.client.media.audio

import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Interface for audio recording functionality across platforms
 */
interface AudioRecordingManager {
    /**
     * Starts audio recording.
     *
     * @param targetNoteId The UUID that the eventual saved audio note will use.
     *   When supplied, the recording manager persists refined transcription
     *   results to [app.logdate.client.repository.transcription.TranscriptionRepository]
     *   under this id, so the polished transcript survives the editor view
     *   model lifecycle, process death, and any later viewer that loads the
     *   note. If null, refinement is in-memory only and is lost when the
     *   recording session ends.
     * @return True if recording started successfully
     */
    suspend fun startRecording(targetNoteId: Uuid? = null): Boolean

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
     * Fire-and-forget request to stop any in-progress recording. Useful from
     * lifecycle hooks (e.g. ViewModel.onCleared) where there is no live
     * coroutine scope to await a suspending [stopRecording] call.
     *
     * Implementations should perform the stop on their own internal scope so
     * the foreground service is released cleanly without dragging an in-flight
     * Whisper refinement pass down with it. Default is a no-op.
     */
    fun requestStopRecording() {}

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

    /**
     * Filesystem path of the active recording target, or null when nothing is recording.
     *
     * Surfaced so the editor can write the path into the entry draft's pending-media list
     * while a recording is in flight — that's the recovery anchor used to validate or
     * delete the file if the process dies before the recording finalizes.
     *
     * Default null so platforms that don't surface a path (or that don't support recording)
     * fall through gracefully; orphan recovery on those platforms degrades to "Failed".
     */
    val currentRecordingPath: String?
        get() = null
}
