package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.feature.editor.ui.editor.AudioCaptureState
import kotlin.uuid.Uuid

/**
 * Drives a pending audio recording to a finalized [AudioCaptureState] before the
 * editor's save path consults the block's state.
 *
 * The race this exists to fix: when a user taps Save, the block may still be in
 * [AudioCaptureState.Empty] / [AudioCaptureState.Recording] / [AudioCaptureState.Stopping]
 * because the recording-side state hasn't yet propagated into the block via the
 * Compose [androidx.compose.runtime.LaunchedEffect] in `AudioBlockEditor`. The
 * save path calls [finalize] for every audio block before mapping to journal
 * notes, so the URI is absorbed deterministically rather than racing the UI.
 *
 * Implementations should:
 * - Return [currentState] unchanged when it is already [AudioCaptureState.Ready] or no
 *   recording is bound to [blockId].
 * - Drive any in-flight recording to completion (calling stop on the underlying
 *   recording manager, awaiting the URI, resolving duration).
 * - Return [AudioCaptureState.Failed] (do not throw) when finalization cannot succeed
 *   so the editor can surface a recoverable error and keep the user in the editor.
 */
fun interface AudioBlockFinalizer {
    suspend fun finalize(
        blockId: Uuid,
        currentState: AudioCaptureState,
    ): AudioCaptureState

    companion object {
        /**
         * Finalizer that returns [currentState] unchanged. Used as a default when no
         * audio runtime is wired (e.g., minimal unit tests for unrelated editor flows).
         */
        val NoOp: AudioBlockFinalizer = AudioBlockFinalizer { _, currentState -> currentState }
    }
}
