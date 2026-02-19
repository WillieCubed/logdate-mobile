package app.logdate.wear.recording

import app.logdate.client.media.audio.AudioPlaybackManager
import io.github.aakira.napier.Napier

/**
 * Stub implementation of AudioPlaybackManager for Wear OS.
 * 
 * This implementation provides no-op functionality for playback operations
 * since we're focusing only on recording in the initial Wear OS implementation.
 * This allows us to reuse the AudioViewModel without implementing full playback.
 */
class StubAudioPlaybackManager : AudioPlaybackManager {
    
    override fun startPlayback(
        uri: String,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit
    ) {
        Napier.d("Playback not implemented on Wear OS yet")
        onPlaybackCompleted()
    }
    
    override fun pausePlayback() {
        Napier.d("Playback not implemented on Wear OS yet")
    }
    
    override fun stopPlayback() {
        Napier.d("Playback not implemented on Wear OS yet")
    }
    
    override fun seekTo(position: Float) {
        Napier.d("Playback not implemented on Wear OS yet")
    }
    
    override fun release() {
        Napier.d("Releasing stub playback manager (no-op)")
    }
}
