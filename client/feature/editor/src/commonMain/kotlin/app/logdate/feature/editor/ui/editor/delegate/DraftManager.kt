package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.domain.notes.drafts.CleanupExpiredDraftsUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.feature.editor.ui.editor.DraftState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.mapper.toDomainBlock
import app.logdate.feature.editor.ui.mapper.toJournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/**
 * Result of loading a draft, containing the converted blocks and the draft ID.
 */
data class LoadedDraft(
    val blocks: List<EntryBlockUiState>,
    val draftId: Uuid,
)

/**
 * Manages all draft operations: auto-save, load, delete, and cleanup.
 */
class DraftManager(
    private val updateEntryDraft: UpdateEntryDraftUseCase,
    private val createEntryDraft: CreateEntryDraftUseCase,
    private val fetchEntryDraft: FetchEntryDraftUseCase,
    private val deleteEntryDraft: DeleteEntryDraftUseCase,
    private val deleteAllDraftsUseCase: DeleteAllDraftsUseCase,
    private val cleanupExpiredDraftsUseCase: CleanupExpiredDraftsUseCase,
) {
    /**
     * Auto-saves the current entry state as a draft.
     *
     * @return The draft ID if a draft was created or updated, null if skipped.
     */
    suspend fun autoSave(state: EditorState): Uuid? {
        val notes =
            state.blocks.mapNotNull { block ->
                if (!block.hasContent() || state.isReadOnly(block.id)) return@mapNotNull null
                block.toJournalNote()
            }

        if (notes.isEmpty()) {
            Napier.d("Skip autosave: no editable content")
            return null
        }

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

    /**
     * Loads a draft by ID and converts its notes to UI blocks.
     */
    suspend fun loadDraft(draftId: Uuid): Result<LoadedDraft> =
        try {
            val result = fetchEntryDraft(draftId).first()
            result.map { draft ->
                LoadedDraft(
                    blocks = draft.notes.map { it.toDomainBlock() },
                    draftId = draft.id,
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Deletes a single draft by ID.
     */
    suspend fun deleteDraft(draftId: Uuid): Result<Unit> =
        try {
            deleteEntryDraft(draftId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Deletes all drafts atomically.
     */
    suspend fun deleteAllDrafts(): Result<Unit> =
        try {
            deleteAllDraftsUseCase()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Removes expired drafts.
     *
     * @return The number of drafts deleted.
     */
    suspend fun cleanupExpired(): Int = cleanupExpiredDraftsUseCase()
}
