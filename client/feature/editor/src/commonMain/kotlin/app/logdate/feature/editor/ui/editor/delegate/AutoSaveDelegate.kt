package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.mapper.toJournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Delegate for handling auto-save functionality in the editor.
 * This separates the auto-saving logic from the ViewModel.
 */
class AutoSaveDelegate(
    private val scope: CoroutineScope,
    private val updateEntryDraft: UpdateEntryDraftUseCase,
    private val createEntryDraft: CreateEntryDraftUseCase,
) {
    /**
     * Auto-saves the current entry state.
     *
     * @param state Current editor state
     * @param onDraftCreated Callback when a new draft is created (to update UI state)
     * @return The draft ID if successful, null otherwise
     */
    fun autoSaveEntry(
        state: EditorState,
        onDraftCreated: (Uuid) -> Unit
    ) {
        scope.launch {
            try {
                // Convert UI blocks to domain notes, filtering out read-only blocks
                val notes = state.blocks.mapNotNull { block ->
                    // Skip read-only blocks and empty blocks
                    if (!block.hasContent() || state.isReadOnly(block.id)) return@mapNotNull null
                    
                    // Convert blocks to notes using the mapper
                    block.toJournalNote()
                }
                
                // Don't save empty drafts
                if (notes.isEmpty()) {
                    Napier.d("Skip autosave: no editable content")
                    return@launch
                }
                
                // Save to draft repository
                val currentDraftId = state.draftId
                val draftId = if (currentDraftId != null) {
                    updateEntryDraft(currentDraftId, notes)
                    currentDraftId
                } else {
                    val newDraftId = createEntryDraft(notes)
                    // Notify the caller of the new draft ID
                    onDraftCreated(newDraftId)
                    newDraftId
                }
                
                Napier.d("Auto-saved draft: $draftId with ${notes.size} notes")
            } catch (e: Exception) {
                Napier.e("Failed to auto-save draft: ${e.message}", e)
                // Re-throw to let the AutoSaveHandler handle retries
                throw e
            }
        }
    }
}