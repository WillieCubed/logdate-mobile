package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_error_recording_not_recovered

/**
 * Resolves audio capture states left behind by a prior session.
 *
 * Drafts persist in-flight recordings via [app.logdate.client.repository.journals.PendingMediaRecord],
 * which reload as [AudioCaptureState.Stopping] blocks on the next launch. The
 * recoverer attempts to validate the file referenced by [AudioCaptureState.Stopping.filePath]
 * and produces a definitive next state:
 *
 * - [AudioCaptureState.Ready] if the file is parseable (its duration can be resolved).
 * - [AudioCaptureState.Failed] if the path is missing or the file is unreadable —
 *   the user can dismiss the block and the cleanup pass deletes the orphan file.
 *
 * Surface recovery errors as [AudioCaptureState.Failed] and propagate cancellation.
 */
interface PendingAudioRecoverer {
    suspend fun recover(state: AudioCaptureState.Stopping): AudioCaptureState
}

/**
 * Default recoverer backed by [AudioDurationResolver].
 *
 * A non-null duration means the file's container header is intact and the audio is
 * usable for playback — sufficient to re-attach to the entry as a finalized recording.
 */
class DefaultPendingAudioRecoverer(
    private val durationResolver: AudioDurationResolver,
    private val recordingManager: AudioRecordingManager? = null,
) : PendingAudioRecoverer {
    override suspend fun recover(state: AudioCaptureState.Stopping): AudioCaptureState {
        var path = state.filePath
        val manager = recordingManager
        try {
            if (path != null && manager?.currentRecordingPath?.removePrefix("file://") == path.removePrefix("file://")) {
                if (manager.isRecording || manager.isStartingRecording) {
                    return AudioCaptureState.Recording(
                        filePath = manager.currentRecordingPath,
                        fallbackFilePath = state.fallbackFilePath,
                    )
                }
                path = manager.stopRecording() ?: path
            }
            path?.let { resolvePath(it) }?.let { return it }
            state.fallbackFilePath
                ?.takeIf { it != path }
                ?.let { resolvePath(it) }
                ?.let { return it }
            return AudioCaptureState.Failed(Res.string.editor_error_recording_not_recovered)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Napier.w("Could not resolve duration for recovered audio $path", error)
            return AudioCaptureState.Failed(Res.string.editor_error_recording_not_recovered)
        }
    }

    private suspend fun resolvePath(path: String): AudioCaptureState.Ready? =
        try {
            durationResolver.resolveDurationMs(path)?.let { AudioCaptureState.Ready(uri = path, durationMs = it) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Napier.w("Could not resolve recovered audio", error)
            null
        }

}
