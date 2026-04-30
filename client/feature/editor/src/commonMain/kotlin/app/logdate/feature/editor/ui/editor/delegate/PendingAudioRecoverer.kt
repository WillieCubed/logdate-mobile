package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.feature.editor.ui.editor.AudioCaptureState

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
 * Implementations must not throw; surface errors as [AudioCaptureState.Failed].
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
) : PendingAudioRecoverer {
    override suspend fun recover(state: AudioCaptureState.Stopping): AudioCaptureState {
        val path = state.filePath ?: return AudioCaptureState.Failed(RECORDING_LOST_REASON)
        val durationMs =
            try {
                durationResolver.resolveDurationMs(path)
            } catch (e: Exception) {
                null
            }
        return if (durationMs != null) {
            AudioCaptureState.Ready(uri = path, durationMs = durationMs)
        } else {
            AudioCaptureState.Failed(RECORDING_LOST_REASON)
        }
    }

    private companion object {
        const val RECORDING_LOST_REASON: String = "Recording could not be recovered"
    }
}
