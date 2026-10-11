package app.logdate.client.media.audio

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

data class AudioPlaybackStatus(
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val duration: Duration = Duration.ZERO,
    val currentUri: String? = null,
    val metadata: AudioPlaybackMetadata? = null,
    val isSuppressedForUnsuitableOutput: Boolean = false,
    /**
     * Set when the most recent playback attempt failed; cleared when a new attempt starts.
     *
     * Only the Android `AudioPlaybackManager` implementation populates this today. iOS and
     * Desktop don't implement [AudioPlaybackStatusProvider], so this stays null on those
     * platforms even when their own playback fails.
     */
    val errorMessage: String? = null,
    /** Where playback is, in milliseconds from the beginning. */
    val positionMs: Long = 0L,
    /** The speed playback runs at; 1 is normal. */
    val playbackSpeed: Float = 1f,
    /** Whether stretches of silence are skipped. Only the Android playback manager sets it. */
    val skipSilenceEnabled: Boolean = false,
    /** Playback intent; remains true during buffering and transient suppression. */
    val playWhenReady: Boolean = isPlaying,
)

interface AudioPlaybackStatusProvider {
    val playbackStatus: StateFlow<AudioPlaybackStatus>
}
