package app.logdate.wear.v2.recorder.gesture

/** What Record's gesture tells the recorder, in the order it can happen. */
interface RecordGestureListener {
    /** A finger is down on Record. It may still turn into a scroll. */
    fun onPressed()

    /** The touch became a scroll, or the system cancelled it, before the long-press timeout. */
    fun onYielded()

    /** A tap: start a recording that goes on after the finger lifts. */
    fun onTapped()

    /** Still down at the long-press timeout: hold to talk. */
    fun onCommitted()

    /** How far the finger has slid toward the lock while holding, from 0 to 1. */
    fun onLockProgress(progress: Float)

    /** Slid all the way up: the recording goes on after the finger lifts. */
    fun onLocked()

    /** The finger lifted while holding, or the system cancelled the touch, before the lock: save the recording. */
    fun onReleased()
}

/** The same as a tap. TalkBack's actions and a click on a ready Record run it. */
fun RecordGestureListener.startLocked() {
    onPressed()
    onTapped()
}

internal fun RecordGestureListener.deliver(signal: RecordGestureSignal) {
    when (signal) {
        RecordGestureSignal.Pressed -> onPressed()
        RecordGestureSignal.Yielded -> onYielded()
        RecordGestureSignal.Tapped -> onTapped()
        RecordGestureSignal.Committed -> onCommitted()
        is RecordGestureSignal.LockProgress -> onLockProgress(signal.progress)
        RecordGestureSignal.Locked -> onLocked()
        RecordGestureSignal.Released -> onReleased()
    }
}
