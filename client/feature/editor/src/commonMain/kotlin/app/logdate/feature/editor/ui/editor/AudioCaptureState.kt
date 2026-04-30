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

    /**
     * A recording is in progress.
     *
     * [filePath] is the durable path the recorder is writing to, when the recording side
     * has surfaced it. May be null in the current wiring, in which case orphan recovery
     * after process death cannot validate the file and the block will surface as [Failed].
     */
    data class Recording(
        val filePath: String? = null,
    ) : AudioCaptureState

    /**
     * Stop has been requested and the recording is being finalized.
     *
     * [filePath] is populated as soon as the recording side returns the URI for the
     * finalized file, which is the right moment for an autosave to persist the
     * recovery anchor into the draft.
     */
    data class Stopping(
        val filePath: String? = null,
    ) : AudioCaptureState

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
