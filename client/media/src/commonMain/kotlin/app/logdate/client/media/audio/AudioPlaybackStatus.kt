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
)

interface AudioPlaybackStatusProvider {
    val playbackStatus: StateFlow<AudioPlaybackStatus>
}
