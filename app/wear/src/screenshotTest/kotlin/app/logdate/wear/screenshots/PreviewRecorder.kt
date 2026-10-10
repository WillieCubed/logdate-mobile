package app.logdate.wear.screenshots

import app.logdate.wear.capture.Feeling
import app.logdate.wear.v2.recorder.RecorderActions
import app.logdate.wear.v2.recorder.gesture.RecordGestureListener

/** A recorder that does nothing, for drawing Today's recorder in previews. */
internal object PreviewRecorder :
    RecordGestureListener,
    RecorderActions {
    override fun onPressed() = Unit

    override fun onYielded() = Unit

    override fun onTapped() = Unit

    override fun onCommitted() = Unit

    override fun onLockProgress(progress: Float) = Unit

    override fun onLocked() = Unit

    override fun onReleased() = Unit

    override fun stop() = Unit

    override fun togglePause() = Unit

    override fun discard() = Unit

    override fun undo() = Unit

    override fun chooseFeeling(feeling: Feeling) = Unit

    override fun retry() = Unit

    override fun dismiss() = Unit
}
