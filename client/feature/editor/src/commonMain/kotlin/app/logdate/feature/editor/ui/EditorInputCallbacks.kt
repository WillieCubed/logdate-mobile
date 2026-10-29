package app.logdate.feature.editor.ui

import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioResolver
import kotlin.uuid.Uuid

internal fun blockUpdateCallback(
    renderedBlocks: List<EntryBlockUiState>,
    renderedInputTokens: Map<Uuid, Uuid>,
    update: (EntryBlockUiState, EntryBlockUiState?, Uuid?) -> Unit,
): (EntryBlockUiState) -> Unit {
    val bases = renderedBlocks.associateBy { it.id }
    val tokens = renderedInputTokens.toMap()
    return { updated -> update(updated, bases[updated.id], tokens[updated.id]) }
}

internal fun audioResolverCallback(
    renderedInputTokens: Map<Uuid, Uuid>,
    currentState: () -> EditorState,
    bind: (Uuid, PendingAudioResolver) -> Unit,
): (Uuid, PendingAudioResolver) -> Unit {
    val tokens = renderedInputTokens.toMap()
    return { id, resolver ->
        val current = currentState()
        if (current.blocks.any { it.id == id && it is AudioBlockUiState } && current.creationInputTokens[id] == tokens[id]) {
            bind(id, resolver)
        }
    }
}

internal fun focusDismissCallback(
    renderedId: Uuid?,
    renderedTokens: Map<Uuid, Uuid>,
    dismiss: (Uuid, Uuid?) -> Boolean,
): () -> Boolean {
    val token = renderedTokens[renderedId]
    return { renderedId != null && dismiss(renderedId, token) }
}
