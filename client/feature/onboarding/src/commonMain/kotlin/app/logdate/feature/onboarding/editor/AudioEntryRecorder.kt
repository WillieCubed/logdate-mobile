package app.logdate.feature.onboarding.editor

/**
 * Interface for recording audio.
 */
interface AudioEntryRecorder {

    /**
     * Whether the recorder is currently recording audio.
     */
    val isRecording: Boolean

    /**
     * Starts recording audio.
     *
     * If the recorder is already recording, this method does nothing.
     */
    fun startRecording(outputFilename: String)

    /**
     * Stops recording audio.
     *
     * If the recorder is not recording, this method does nothing.
     */
    fun stopRecording()

    /**
     * Cancels the current recording.
     */
    fun cancelRecording()
}