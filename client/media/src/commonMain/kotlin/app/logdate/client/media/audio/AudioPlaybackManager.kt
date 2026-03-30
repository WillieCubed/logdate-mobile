package app.logdate.client.media.audio

/**
 * Interface defining operations for playing back audio files.
 */
interface AudioPlaybackManager {
    /**
     * Starts playing audio from the given URI.
     *
     * @param uri The URI of the audio file to play.
     * @param metadata Optional metadata for system UI surfaces (notifications, lock screen).
     * @param onProgressUpdated Callback for playback progress updates (0.0f to 1.0f).
     * @param onPlaybackCompleted Callback when playback is complete.
     */
    fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata? = null,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
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
     * Seeks to an absolute position in the audio file.
     *
     * The default implementation keeps existing playback managers source-compatible by
     * converting the timestamp to a normalized progress ratio.
     *
     * @param positionMs Absolute position in milliseconds.
     * @param durationMs Total audio duration in milliseconds.
     */
    fun seekTo(
        positionMs: Long,
        durationMs: Long,
    ) {
        if (durationMs <= 0L) return
        seekTo((positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f))
    }

    /**
     * Cleans up any resources.
     */
    fun release()
}
