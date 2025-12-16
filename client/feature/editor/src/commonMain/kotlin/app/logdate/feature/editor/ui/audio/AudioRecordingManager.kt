package app.logdate.feature.editor.ui.audio

/**
 * Interface defining operations for recording audio.
 */
interface AudioRecordingManager {
    /**
     * Starts recording audio.
     * 
     * @param onAudioLevelChanged Callback for audio level updates.
     * @param onDurationChanged Callback for recording duration updates.
     */
    fun startRecording(
        onAudioLevelChanged: (List<Float>) -> Unit,
        onDurationChanged: (Long) -> Unit
    )
    
    /**
     * Stops the current recording.
     * 
     * @return The URI of the saved recording.
     */
    fun stopRecording(): String?
    
    /**
     * Clears any recording resources.
     */
    fun clear()
    
    /**
     * Pauses the current recording if supported by the platform.
     * Default implementation does nothing and returns false.
     * 
     * @return True if the recording was successfully paused, false otherwise.
     */
    suspend fun pauseRecording(): Boolean {
        return false
    }
    
    /**
     * Resumes a paused recording if supported by the platform.
     * Default implementation does nothing and returns false.
     * 
     * @return True if the recording was successfully resumed, false otherwise.
     */
    suspend fun resumeRecording(): Boolean {
        return false
    }
}
