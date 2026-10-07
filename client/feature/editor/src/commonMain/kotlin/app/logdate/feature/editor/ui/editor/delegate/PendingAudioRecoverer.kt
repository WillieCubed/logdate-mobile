package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

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
        var path = state.filePath ?: return AudioCaptureState.Failed(RECORDING_LOST_REASON)
        val manager = recordingManager
        try {
            if (manager?.currentRecordingPath?.removePrefix("file://") == path.removePrefix("file://")) {
                if (manager.isRecording || manager.isStartingRecording) {
                    return AudioCaptureState.Recording(filePath = manager.currentRecordingPath)
                }
                path = manager.stopRecording() ?: path
            }
            val durationMs = durationResolver.resolveDurationMs(path)
            return if (durationMs != null) {
                AudioCaptureState.Ready(uri = path, durationMs = durationMs)
            } else {
                AudioCaptureState.Failed(RECORDING_LOST_REASON)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Napier.w("Could not resolve duration for recovered audio $path", error)
            return AudioCaptureState.Failed(RECORDING_LOST_REASON)
        }
    }

    private companion object {
        const val RECORDING_LOST_REASON: String = "Recording could not be recovered"
    }
}
