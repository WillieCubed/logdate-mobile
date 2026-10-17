package app.logdate.feature.editor.ui.editor

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_error_audio_duration_unreadable

/** Reads the managed file's metadata before an unknown-duration attachment can be published. */
internal suspend fun EntryEditorViewModel.resolveAudioDuration(state: AudioCaptureState.Ready): AudioCaptureState {
    if (state.durationMs > 0L) return state
    val duration =
        try {
            audioDurationResolver?.resolveDurationMs(state.uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.w("Could not read imported audio duration", error)
            null
        }
    return if (duration != null && duration > 0L) {
        state.copy(durationMs = duration)
    } else {
        AudioCaptureState.Failed(Res.string.editor_error_audio_duration_unreadable)
    }
}
