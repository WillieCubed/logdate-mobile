package app.logdate.feature.editor.ui.audio

import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackStatusProvider
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.capture_state_playback_failed
import logdate.client.feature.editor.generated.resources.capture_state_playback_start_failed

/** Owns playback actions and status while the ViewModel coordinates recording transitions. */
internal class AudioPlaybackController(
    private val state: MutableStateFlow<AudioUiState>,
    private val manager: AudioPlaybackManager,
    private val statusProvider: AudioPlaybackStatusProvider?,
    private val scope: CoroutineScope,
    private val stopRecordingBeforePlayback: suspend () -> Unit,
) {
    fun toggle(
        uri: String,
        metadata: AudioPlaybackMetadata?,
    ) {
        if (state.value.isPlaying) pause() else start(uri, metadata)
    }

    fun start(
        uri: String,
        metadata: AudioPlaybackMetadata?,
    ) {
        scope.launch {
            Napier.d("AudioViewModel: Starting playback of $uri")
            try {
                stopRecordingBeforePlayback()
                state.update { it.copy(isPlaying = true, currentUri = uri, isRecording = false, isPaused = false) }
                manager.startPlayback(
                    uri = uri,
                    metadata = metadata,
                    onProgressUpdated = { progress -> state.update { it.copy(playbackProgress = progress) } },
                    onPlaybackCompleted = { completePlayback() },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Napier.e("Failed to start playback: ${error.message}", error)
                state.update { it.copy(isPlaying = false, error = Res.string.capture_state_playback_start_failed) }
            }
        }
    }

    private fun completePlayback() {
        val failure = statusProvider?.playbackStatus?.value?.errorMessage
        state.update {
            it.copy(
                isPlaying = false,
                playbackProgress = 0f,
                error = if (failure != null) Res.string.capture_state_playback_failed else null,
            )
        }
    }

    fun pause() {
        scope.launch {
            Napier.d("AudioViewModel: Pausing playback")
            try {
                manager.pausePlayback()
                state.update { it.copy(isPlaying = false) }
            } catch (error: Exception) {
                Napier.e("Failed to pause playback: ${error.message}", error)
            }
        }
    }

    fun seek(position: Float) {
        scope.launch {
            Napier.d("AudioViewModel: Seeking to $position")
            try {
                manager.seekTo(position)
                state.update { it.copy(playbackProgress = position) }
            } catch (error: Exception) {
                Napier.e("Failed to seek: ${error.message}", error)
            }
        }
    }

    fun seek(
        positionMs: Long,
        durationMs: Long,
    ) {
        scope.launch {
            Napier.d("AudioViewModel: Seeking to ${positionMs}ms")
            try {
                manager.seekTo(positionMs, durationMs)
                if (durationMs > 0L) {
                    state.update {
                        it.copy(playbackProgress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f))
                    }
                }
            } catch (error: Exception) {
                Napier.e("Failed to seek to ${positionMs}ms: ${error.message}", error)
            }
        }
    }

    fun stopIfPlaying() {
        if (state.value.isPlaying) manager.stopPlayback()
    }
}
