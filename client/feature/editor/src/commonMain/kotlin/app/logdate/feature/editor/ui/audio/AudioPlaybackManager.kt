package app.logdate.feature.editor.ui.audio

/**
 * Interface defining operations for playing back audio files.
 */
interface AudioPlaybackManager {
    /**
     * Starts playing audio from the given URI.
     * 
     * @param uri The URI of the audio file to play.
     * @param onProgressUpdated Callback for playback progress updates (0.0f to 1.0f).
     * @param onPlaybackCompleted Callback when playback is complete.
     */
    fun startPlayback(
        uri: String,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit
    )
    
    /**
     * Pauses the current playback.
     */
    fun pausePlayback()
    
    /**
     * Stops and resets the current playback.
     */
    fun stopPlayback()
    
    /**
     * Seeks to a specific position in the audio file.
     * 
     * @param position Position in the range 0.0f to 1.0f.
     */
    fun seekTo(position: Float)
    
    /**
     * Cleans up any resources.
     */
    fun release()
}