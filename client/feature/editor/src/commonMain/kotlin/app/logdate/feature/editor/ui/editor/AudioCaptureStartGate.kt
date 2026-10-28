package app.logdate.feature.editor.ui.editor

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

internal interface AudioCaptureStartGate {
    fun claim(blockId: Uuid): Uuid?

    fun release(
        blockId: Uuid,
        token: Uuid,
    )
}

/** Standalone content may render controls, but only an editor owner can authorize capture. */
internal val LocalAudioCaptureStartGate = staticCompositionLocalOf<AudioCaptureStartGate?> { null }

internal class EditorAudioCaptureStartGate(
    private val state: MutableStateFlow<EditorState>,
    private val observed: () -> EditorState = { state.value },
) : AudioCaptureStartGate {
    constructor(viewModel: EntryEditorViewModel) : this(viewModel.mutableEditorState, { viewModel.editorState.value })

    fun runWhenIdle(action: () -> Unit) {
        if (state.value.audioCaptureStartReservations.isEmpty()) action()
    }

    override fun claim(blockId: Uuid): Uuid? = state.claimAudioCaptureStart(blockId, observed)

    override fun release(
        blockId: Uuid,
        token: Uuid,
    ) = state.releaseAudioCaptureStart(blockId, token)
}

internal fun EntryEditorViewModel.claimAudioCaptureStart(blockId: Uuid): Uuid? =
    mutableEditorState.claimAudioCaptureStart(blockId) { editorState.value }

internal fun EntryEditorViewModel.releaseAudioCaptureStart(
    blockId: Uuid,
    token: Uuid,
) = mutableEditorState.releaseAudioCaptureStart(blockId, token)

private fun MutableStateFlow<EditorState>.claimAudioCaptureStart(
    blockId: Uuid,
    observed: () -> EditorState,
): Uuid? {
    val token = Uuid.random()
    while (true) {
        val visible = observed()
        if (visible.isLoading || visible.isReadOnly(blockId)) return null
        val current = value
        if (current.isSaving ||
            current.isEditingLocked ||
            current.shouldExit ||
            current.isReadOnly(blockId) ||
            current.isRecordingAudio ||
            current.audioCaptureStartReservations.isNotEmpty()
        ) {
            return null
        }
        val block = current.blocks.firstOrNull { it.id == blockId } as? AudioBlockUiState ?: return null
        if (block.captureState != AudioCaptureState.Empty) return null
        val reserved = current.copy(audioCaptureStartReservations = current.audioCaptureStartReservations + (blockId to token))
        if (compareAndSet(current, reserved)) {
            return token
        }
    }
}

private fun MutableStateFlow<EditorState>.releaseAudioCaptureStart(
    blockId: Uuid,
    token: Uuid,
) {
    while (true) {
        val current = value
        if (current.audioCaptureStartReservations[blockId] != token) return
        if (compareAndSet(current, current.copy(audioCaptureStartReservations = current.audioCaptureStartReservations - blockId))) return
    }
}

internal fun startReservedAudioCapture(
    gate: AudioCaptureStartGate?,
    blockId: Uuid,
    start: () -> Job,
): Job? {
    val token = gate?.claim(blockId) ?: return null
    return try {
        start().also { job -> job.invokeOnCompletion { gate.release(blockId, token) } }
    } catch (failure: Throwable) {
        gate.release(blockId, token)
        throw failure
    }
}
