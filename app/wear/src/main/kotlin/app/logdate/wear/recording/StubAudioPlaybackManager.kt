package app.logdate.wear.recording

import app.logdate.feature.editor.ui.audio.AudioPlaybackManager
import io.github.aakira.napier.Napier

/**
 * Stub implementation of AudioPlaybackManager for Wear OS.
 * 
 * This implementation provides no-op functionality for playback operations
 * since we're focusing only on recording in the initial Wear OS implementation.
 * This allows us to reuse the AudioViewModel without implementing full playback.
 */
class StubAudioPlaybackManager : AudioPlaybackManager {
    
    override suspend fun startPlayback(
        uri: String,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit
    ) {
        Napier.d("Playback not implemented on Wear OS yet")
        onPlaybackCompleted()
    }
    
    override suspend fun pausePlayback() {
        Napier.d("Playback not implemented on Wear OS yet")
    }
    
    override suspend fun resumePlayback() {
        Napier.d("Playback not implemented on Wear OS yet")
    }
    
    override suspend fun seekTo(position: Float) {
        Napier.d("Playback not implemented on Wear OS yet")
    }
    
    override fun release() {
        Napier.d("Releasing stub playback manager (no-op)")
    }
}