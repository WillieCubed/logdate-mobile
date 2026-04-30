package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.feature.editor.ui.editor.AudioCaptureState
import kotlin.uuid.Uuid

/**
 * Side-channel through which the audio recording side surfaces pending recordings
 * so the editor's save path can absorb them.
 *
 * The implementation lives on the recording-side ViewModel (AudioViewModel) and
 * exposes a narrow surface to the editor side via [DefaultAudioBlockFinalizer]
 * so the two ViewModels don't need direct bidirectional coupling.
 *
 * The resolver covers two scenarios that the Compose-side
 * [androidx.compose.runtime.LaunchedEffect] in `AudioBlockEditor` cannot
 * deterministically guarantee:
 *
 * 1. **Recording just stopped**: a URI sits on the recording side waiting for
 *    the LaunchedEffect to copy it into the block. If save races past the
 *    effect, [resolvePending] returns the [AudioCaptureState.Ready] holding
 *    that URI and clears the side state.
 * 2. **Recording is still in flight**: the user tapped Save while recording.
 *    [resolvePending] drives the recorder to stop, awaits the URI, and
 *    returns the resulting state.
 *
 * Implementations must return null when no pending recording is associated
 * with [blockId] — the finalizer will then leave the block's existing state
 * unchanged (the mapper drops empty audio blocks, which is correct).
 */
interface PendingAudioResolver {
    suspend fun resolvePending(blockId: Uuid): AudioCaptureState?
}
