package app.logdate.feature.editor.ui.audio

import app.logdate.client.media.audio.transcription.TimedTranscript
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * Data class representing the UI state for audio recording and playback.
 */
data class AudioUiState(
    // Recording state
    val isRecording: Boolean = false,
    val isPaused: Boolean = false, // New state for pause/resume functionality
    val recordedAudioUri: String? = null,
    val audioLevels: List<Float> = emptyList(),
    val duration: Duration = ZERO,
    // Playback state
    val isPlaying: Boolean = false,
    val playbackProgress: Float = 0f,
    val currentUri: String? = null,
    // Shared state
    val error: String? = null,
    // Transcription state
    val transcriptionState: TranscriptionState = TranscriptionState.NotRequested,
) {
    /**
     * States for transcription UI.
     */
    sealed class TranscriptionState {
        /**
         * Transcription has not been requested.
         */
        object NotRequested : TranscriptionState()

        /**
         * Transcription is queued but not started.
         */
        object Pending : TranscriptionState()

        /**
         * Transcription is in progress.
         */
        object InProgress : TranscriptionState()

        /**
         * Transcription completed successfully.
         *
         * @param isRefining true while a higher-accuracy refinement pass is
         *   still rewriting parts of [text] in the background. The UI should
         *   accept that the text may continue to change visibly while this
         *   flag is true and crossfade between values smoothly.
         */
        data class Success(
            val text: String,
            val timedTranscript: TimedTranscript? = null,
            val isFinal: Boolean = false,
            val isRefining: Boolean = false,
        ) : TranscriptionState()

        /**
         * Transcription failed.
         */
        data class Error(
            val message: String,
        ) : TranscriptionState()
    }

    // Compatibility properties to maintain backward compatibility
    val transcription: String?
        get() =
            when (val state = transcriptionState) {
                is TranscriptionState.Success -> state.text
                else -> null
            }

    val timedTranscript: TimedTranscript?
        get() =
            when (val state = transcriptionState) {
                is TranscriptionState.Success -> state.timedTranscript
                else -> null
            }

    val transcriptionInProgress: Boolean
        get() =
            transcriptionState is TranscriptionState.InProgress ||
                transcriptionState is TranscriptionState.Pending
}
