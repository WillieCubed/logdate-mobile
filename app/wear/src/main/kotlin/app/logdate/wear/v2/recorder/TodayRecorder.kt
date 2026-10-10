package app.logdate.wear.v2.recorder

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import app.logdate.wear.R
import app.logdate.wear.v2.recorder.gesture.RecordGestureListener

/** The recorder as Today shows it: its state, the gesture it listens to, and its controls. */
@Stable
class TodayRecorder(
    val state: RecorderState,
    val gesture: RecordGestureListener,
    val actions: RecorderActions,
)

/** What a click on Record does. A touch on an armed Record goes through the gesture instead. */
internal enum class RecordButtonAction {
    /** Starts a locked recording, as TalkBack's click does. */
    START,
    RETRY_SAVE,

    /** A recording is starting, or is hold to talk; a click does nothing. */
    NONE,
}

/**
 * Record in one recorder state.
 *
 * @property armed Whether a touch goes through the record gesture.
 * @property morph 0 draws the record circle and 1 the square a hold reaches at the lock.
 */
internal data class RecordButtonModel(
    val action: RecordButtonAction,
    val armed: Boolean,
    val enabled: Boolean,
    val morph: Float,
    @param:StringRes val descriptionRes: Int,
)

/**
 * Whether Today shows Record as its edge button. A locked recording has Stop in the list instead:
 * the list is as tall as the screen allows, so as the edge button Stop would show only a sliver
 * until the list is scrolled to its end, which pushes the time off the top.
 */
internal val RecorderState.showsRecordButton: Boolean
    get() = !(this is RecorderState.Live && mode == LiveMode.LOCKED)

internal fun RecorderState.recordButton(): RecordButtonModel =
    when (this) {
        RecorderState.Ready, RecorderState.TooShort, is RecorderState.Saved -> startButton
        is RecorderState.Failed -> if (failure == RecorderFailure.SAVE_FAILED) retrySaveButton else startButton
        RecorderState.Arming -> waitingButton(morph = 0f, descriptionRes = R.string.wear_today_record)
        is RecorderState.Live -> waitingButton(lockProgress, R.string.wear_today_recorder_recording)
        RecorderState.Saving -> waitingButton(morph = 1f, descriptionRes = R.string.wear_today_recorder_saving, enabled = false)
    }

/** Record while a recording starts, runs as hold to talk, or saves: a touch passes through and a click does nothing. */
private fun waitingButton(
    morph: Float,
    @StringRes descriptionRes: Int,
    enabled: Boolean = true,
): RecordButtonModel =
    RecordButtonModel(RecordButtonAction.NONE, armed = false, enabled = enabled, morph = morph, descriptionRes = descriptionRes)

private val startButton =
    RecordButtonModel(RecordButtonAction.START, armed = true, enabled = true, morph = 0f, descriptionRes = R.string.wear_today_record)
private val retrySaveButton =
    RecordButtonModel(
        RecordButtonAction.RETRY_SAVE,
        armed = false,
        enabled = true,
        morph = 0f,
        descriptionRes = R.string.wear_today_recorder_save_again,
    )
