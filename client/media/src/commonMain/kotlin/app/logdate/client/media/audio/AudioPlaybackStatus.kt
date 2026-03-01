package app.logdate.client.media.audio

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

data class AudioPlaybackStatus(
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val duration: Duration = Duration.ZERO,
)

interface AudioPlaybackStatusProvider {
    val playbackStatus: StateFlow<AudioPlaybackStatus>
}
