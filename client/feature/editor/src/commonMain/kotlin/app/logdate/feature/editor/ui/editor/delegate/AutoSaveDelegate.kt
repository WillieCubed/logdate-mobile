package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.feature.editor.ui.editor.DraftState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.mapper.toJournalNote
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Delegate for handling auto-save functionality in the editor.
 * This separates the auto-saving logic from the ViewModel.
 *
 * All methods are suspend functions — the caller (ViewModel) is responsible
 * for launching them in a lifecycle-bound scope.
 */
class AutoSaveDelegate(
    private val updateEntryDraft: UpdateEntryDraftUseCase,
    private val createEntryDraft: CreateEntryDraftUseCase,
) {
    /**
     * Auto-saves the current entry state.
     *
     * @param state Current editor state
     * @return The draft ID if a draft was created or updated, null if skipped
     */
    suspend fun autoSaveEntry(state: EditorState): Uuid? {
        // Convert UI blocks to domain notes, filtering out read-only blocks
        val notes =
            state.blocks.mapNotNull { block ->
                // Skip read-only blocks and empty blocks
                if (!block.hasContent() || state.isReadOnly(block.id)) return@mapNotNull null

                // Convert blocks to notes using the mapper
                block.toJournalNote()
            }

        // Don't save empty drafts
        if (notes.isEmpty()) {
            Napier.d("Skip autosave: no editable content")
            return null
        }

        // Update existing draft or create a new one
        val draftId =
            when (val draft = state.draftState) {
                is DraftState.Active -> {
                    updateEntryDraft(draft.id, notes)
                    draft.id
                }
                DraftState.None -> createEntryDraft(notes)
            }

        Napier.d("Auto-saved draft: $draftId with ${notes.size} notes")
        return draftId
    }
}
