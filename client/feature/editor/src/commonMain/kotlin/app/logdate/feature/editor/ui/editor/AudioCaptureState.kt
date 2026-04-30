package app.logdate.feature.editor.ui.editor

/**
 * Lifecycle of an audio recording that belongs to an [AudioBlockUiState].
 *
 * The block carries this state directly so the editor's save and autosave paths
 * can reason about whether a recording is in flight, finalized, or absent —
 * without consulting the singleton AudioViewModel where the file URI used to
 * live ephemerally before being copied into the block via a Compose side effect.
 *
 * Transitions:
 * ```
 *   Empty ── start recording ──▶ Recording ── stop ──▶ Stopping ──┬─▶ Ready(uri, durationMs)
 *                                                                  └─▶ Failed
 *   Ready ── delete ──▶ Empty
 *   Failed ── retry ──▶ Empty
 * ```
 *
 * Only [Ready] is persistable as a [JournalNote.Audio]. Other states represent
 * intent or transient progress and are tracked separately for draft persistence
 * (see the entry draft's pending-media list, introduced alongside this type).
 */
sealed interface AudioCaptureState {
    /** The block has no recording attached. */
    data object Empty : AudioCaptureState

    /** A recording is in progress; no file is yet available. */
    data object Recording : AudioCaptureState

    /** Stop has been requested; the file is being closed and its URI awaited. */
    data object Stopping : AudioCaptureState

    /** Recording is finalized. [uri] points at the durable file; [durationMs] is the resolved length. */
    data class Ready(
        val uri: String,
        val durationMs: Long,
    ) : AudioCaptureState

    /** Recording could not be finalized. [reason] is suitable for surfacing to the user. */
    data class Failed(
        val reason: String,
    ) : AudioCaptureState
}
