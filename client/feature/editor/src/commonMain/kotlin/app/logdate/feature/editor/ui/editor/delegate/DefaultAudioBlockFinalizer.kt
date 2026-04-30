package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.feature.editor.ui.editor.AudioCaptureState
import kotlin.uuid.Uuid

/**
 * Production [AudioBlockFinalizer] that defers to a [PendingAudioResolver] supplied
 * by the audio recording side.
 *
 * Returns [currentState] unchanged when the block is already [AudioCaptureState.Ready]
 * or when the resolver reports no pending recording for [blockId].
 */
class DefaultAudioBlockFinalizer(
    private val resolver: PendingAudioResolver,
) : AudioBlockFinalizer {
    override suspend fun finalize(
        blockId: Uuid,
        currentState: AudioCaptureState,
    ): AudioCaptureState {
        if (currentState is AudioCaptureState.Ready) return currentState
        return resolver.resolvePending(blockId) ?: currentState
    }
}
