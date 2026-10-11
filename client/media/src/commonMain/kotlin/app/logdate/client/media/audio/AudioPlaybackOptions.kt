package app.logdate.client.media.audio

/**
 * How a recording starts.
 *
 * @property startPositionMs Where playback starts, in milliseconds from the beginning.
 * @property speed The playback speed; 1 is normal.
 * @property skipSilence Whether stretches of silence are skipped.
 */
data class AudioPlaybackOptions(
    val startPositionMs: Long = 0L,
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
)
