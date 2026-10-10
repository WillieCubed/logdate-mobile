package app.logdate.wear.v2.recorder

import app.logdate.wear.capture.Feeling

/** What Today's recorder controls do. [RecorderViewModel] implements it; previews and tests use stand-ins. */
interface RecorderActions {
    /** Saves a locked recording. */
    fun stop()

    /** Pauses or resumes a locked recording. */
    fun togglePause()

    /** The first call asks for confirmation; a second within three seconds deletes the recording. */
    fun discard()

    /** Removes the recording just saved. */
    fun undo()

    /** Saves [feeling] as a check-in beside the recording just saved. */
    fun chooseFeeling(feeling: Feeling)

    /** Tries the failed step again: the save, or the microphone request. */
    fun retry()

    /** Clears a failure. */
    fun dismiss()
}
