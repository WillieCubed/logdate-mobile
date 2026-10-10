package app.logdate.wear.v2.haptics

import app.logdate.ui.platform.LogDateHaptics

/** A [LogDateHaptics] that records the name of each event it is asked to play. */
class RecordedHaptics : LogDateHaptics {
    val played = mutableListOf<String>()

    override fun saveSucceeded() {
        played += "saveSucceeded"
    }

    override fun recordingStarted() {
        played += "recordingStarted"
    }

    override fun recordingFinished() {
        played += "recordingFinished"
    }

    override fun transcriptionReady() {
        played += "transcriptionReady"
    }

    override fun confirmDestruction() {
        played += "confirmDestruction"
    }

    override fun recordingLocked() {
        played += "recordingLocked"
    }

    override fun recordingPaused() {
        played += "recordingPaused"
    }

    override fun recordingResumed() {
        played += "recordingResumed"
    }

    override fun momentMarked() {
        played += "momentMarked"
    }

    override fun limitApproaching() {
        played += "limitApproaching"
    }

    override fun actionRejected() {
        played += "actionRejected"
    }

    override fun detentTick() {
        played += "detentTick"
    }
}
