package app.logdate.wear.v2.recorder

import app.logdate.wear.capture.Feeling
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** What the recorder is doing, as Today shows it. */
sealed interface RecorderState {
    /** Nothing is recording; Record starts a recording. */
    data object Ready : RecorderState

    /**
     * A finger is on Record and the gesture hasn't decided between a tap, a hold and a scroll. The
     * recorder may already be running; nothing on screen changes until the gesture decides.
     */
    data object Arming : RecorderState

    /** Recording, by hold to talk or locked. */
    data class Live(
        val mode: LiveMode,
        val paused: Boolean = false,
        val pausedByInterruption: Boolean = false,
        val lockProgress: Float = 0f,
        val elapsed: Duration = Duration.ZERO,
        val levels: List<Float> = emptyList(),
        val confirmingDiscard: Boolean = false,
    ) : RecorderState

    data object Saving : RecorderState

    /** Saved as [noteId]. Undo works while this state lasts; the five feelings while [feelingsOffered]. */
    data class Saved(
        val noteId: Uuid,
        val duration: Duration,
        val feelingsOffered: Boolean,
        val feeling: Feeling? = null,
    ) : RecorderState

    /** The recording was under half a second and was discarded. */
    data object TooShort : RecorderState

    data class Failed(
        val failure: RecorderFailure,
    ) : RecorderState
}

enum class LiveMode {
    /** Hold to talk: the recording saves when the finger lifts. */
    HOLDING,

    /** Goes on after the finger lifts; Stop saves it. */
    LOCKED,
}

/** Why recording or saving failed, so Today can say what to do. */
enum class RecorderFailure {
    /** The microphone permission was refused; asking again shows the system prompt. */
    MICROPHONE_DENIED,

    /** The microphone permission was refused for good; only Settings can grant it. */
    MICROPHONE_BLOCKED,
    NOT_ENOUGH_STORAGE,
    RECORDER_UNAVAILABLE,

    /** The recording finished but could not be stored. Its file is intact and trying again saves it. */
    SAVE_FAILED,

    /** The recorder produced no file, so there is nothing to save. */
    RECORDING_LOST,
}

/**
 * Whether a touch on Record starts a recording, rather than doing what the button shows. While a
 * failed save waits, Record tries the save again instead of starting over.
 */
val RecorderState.canStartRecording: Boolean
    get() =
        when (this) {
            RecorderState.Ready, RecorderState.TooShort, is RecorderState.Saved -> true
            is RecorderState.Failed -> failure != RecorderFailure.SAVE_FAILED
            RecorderState.Arming, is RecorderState.Live, RecorderState.Saving -> false
        }
