package app.logdate.feature.editor.ui.editor

import kotlin.uuid.Uuid

/** A transient viewport request for the input accepted by an explicit draft load. */
data class EditorDraftReveal(
    val blockId: Uuid,
    val inputToken: Uuid,
)

internal fun EntryEditorViewModel.consumeDraftReveal(request: EditorDraftReveal): Boolean {
    while (true) {
        val current = mutableEditorState.value
        if (current.pendingDraftReveal != request) return false
        val eligible =
            current.creationInputTokens[request.blockId] == request.inputToken &&
                current.blocks.any { it.id == request.blockId } &&
                current.expandedBlockId == null &&
                !current.isLoading &&
                !current.isSaving &&
                !current.isEditingLocked &&
                !current.shouldExit &&
                !current.isRecordingAudio &&
                current.audioCaptureStartReservations.isEmpty()
        if (mutableEditorState.compareAndSet(current, current.copy(pendingDraftReveal = null))) return eligible
    }
}

/** Live user intent cancels the latest load, even before the rendered flow observes it. */
internal fun EntryEditorViewModel.cancelDraftReveal() {
    while (true) {
        val current = mutableEditorState.value
        if (current.pendingDraftReveal == null) return
        if (mutableEditorState.compareAndSet(current, current.copy(pendingDraftReveal = null))) return
    }
}
