package app.logdate.feature.editor.ui.editor

import app.logdate.client.text.UiText
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel.LoadRequest
import kotlin.uuid.Uuid

internal data class EditorContentLoad(
    val request: LoadRequest,
    val generation: Long,
    val contentRevision: Long,
)

private fun EditorState.canReplaceLoadedInput(): Boolean =
    !isSaving && !isEditingLocked && !shouldExit && !isRecordingAudio && audioCaptureStartReservations.isEmpty()

/** Reserves the load synchronously so a stale Record action cannot claim an input during its fetch. */
internal fun EntryEditorViewModel.beginEditorContentLoad(request: LoadRequest): EditorContentLoad? {
    if (activeLoadRequest == request) return null
    while (true) {
        val current = mutableEditorState.value
        if (!current.canReplaceLoadedInput()) return null
        if (mutableEditorState.compareAndSet(current, current.copy(isLoading = true))) {
            activeLoadRequest = request
            return EditorContentLoad(request, ++loadGeneration, current.contentRevision)
        }
    }
}

private fun EntryEditorViewModel.ownsLoad(load: EditorContentLoad): Boolean =
    loadGeneration == load.generation && activeLoadRequest == load.request

/** Only an accepted replacement changes input generations; failed/replayed loads keep their inputs. */
internal fun EntryEditorViewModel.acceptEditorContentLoad(
    load: EditorContentLoad,
    replacement: (EditorState) -> EditorState,
): Boolean {
    while (true) {
        if (!ownsLoad(load)) return false
        val current = mutableEditorState.value
        if (current.contentRevision != load.contentRevision || !current.canReplaceLoadedInput()) {
            finishRejectedEditorContentLoad(load)
            return false
        }
        val loaded = replacement(current)
        val tokens = current.creationInputTokens + loaded.blocks.associate { it.id to Uuid.random() }
        val reveal =
            loaded.blocks
                .firstOrNull()
                ?.takeIf {
                    load.request is LoadRequest.Draft && loaded.expandedBlockId == null && !loaded.isRecordingAudio
                }?.let { EditorDraftReveal(it.id, tokens.getValue(it.id)) }
        val accepted =
            loaded.copy(
                contentRevision = current.contentRevision + 1,
                creationInputTokens = tokens,
                pendingDraftReveal = reveal,
                isLoading = false,
            )
        val previousFinalizers = audioBlockFinalizers.toMap()
        if (mutableEditorState.compareAndSet(current, accepted)) {
            for ((id, previous) in previousFinalizers) {
                if (audioBlockFinalizers[id] === previous) audioBlockFinalizers.remove(id)
            }
            return true
        }
    }
}

/** A superseded load cannot clear the newer load's flag, error, tokens or active-request guard. */
internal fun EntryEditorViewModel.finishRejectedEditorContentLoad(
    load: EditorContentLoad,
    errorMessage: UiText? = null,
) {
    while (true) {
        if (!ownsLoad(load)) return
        val current = mutableEditorState.value
        val contentUnchanged = current.contentRevision == load.contentRevision
        val reportError = contentUnchanged && current.canReplaceLoadedInput() && errorMessage != null
        val next =
            current.copy(
                isLoading = false,
                errorMessage = if (reportError) errorMessage else current.errorMessage,
            )
        if (mutableEditorState.compareAndSet(current, next)) {
            // Retain replay protection when newer input invalidated an initial route load.
            if (contentUnchanged && ownsLoad(load)) activeLoadRequest = null
            return
        }
    }
}
