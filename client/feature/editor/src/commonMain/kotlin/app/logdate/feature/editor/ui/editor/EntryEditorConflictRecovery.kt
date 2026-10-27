package app.logdate.feature.editor.ui.editor

import kotlin.uuid.Uuid

/** Writes a separate durable draft before publishing a version that must not replace another edit. */
internal fun EntryEditorViewModel.saveConflictingVersionSeparatelyImpl() {
    val state = editorState.value
    val base = state.entryEditBase ?: return
    if (!state.entryEditConflict || state.isSaving || state.isEditingLocked || state.shouldExit) return
    val id = Uuid.random()
    if (!mutateEditorContent { current ->
            if (current.contentRevision != state.contentRevision || !current.entryEditConflict) {
                current
            } else {
                current.copy(
                    blocks = current.blocks.map { if (it.id == base.targetId) it.withId(id) else it },
                    entryEditBase = base.copy(copyNoteId = id),
                    entryEditConflict = false,
                    errorMessage = null,
                    isModified = true,
                )
            }
        }
    ) {
        return
    }
    saveEntry(currentDraftPersistenceState(mutableEditorState.value))
}

private fun EntryBlockUiState.withId(id: Uuid): EntryBlockUiState =
    when (this) {
        is TextBlockUiState -> copy(id = id)
        is HandwritingBlockUiState -> copy(id = id)
        is ImageBlockUiState -> copy(id = id)
        is AudioBlockUiState -> copy(id = id)
        is VideoBlockUiState -> copy(id = id)
        is CameraBlockUiState -> copy(id = id)
    }
