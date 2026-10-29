package app.logdate.feature.editor.ui.editor

import kotlin.uuid.Uuid

/** Returns the exact accepted revision, independent of the slower rendered state stream. */
internal fun EntryEditorViewModel.commitTextInput(
    id: Uuid,
    inputToken: Uuid?,
    text: String,
): Long? {
    while (true) {
        val current = mutableEditorState.value
        if (current.isLoading ||
            current.isSaving ||
            current.isEditingLocked ||
            current.shouldExit ||
            current.isReadOnly(id) ||
            editorState.value.isReadOnly(id) ||
            current.creationInputTokens[id] != inputToken
        ) {
            return null
        }
        val block = current.blocks.find { it.id == id } as? TextBlockUiState ?: return null
        if (block.content == text) return current.contentRevision
        val next =
            current.copy(
                blocks = current.blocks.map { if (it.id == id) block.copy(content = text) else it },
                contentRevision = current.contentRevision + 1,
                isModified = true,
            )
        if (mutableEditorState.compareAndSet(current, next)) return next.contentRevision
    }
}
