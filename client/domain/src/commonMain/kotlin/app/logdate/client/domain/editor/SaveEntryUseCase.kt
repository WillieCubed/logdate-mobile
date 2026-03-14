package app.logdate.client.domain.editor

import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.repository.journals.JournalNote
import kotlin.uuid.Uuid

/**
 * Publishes editor notes as permanent entries and cleans up the active draft.
 */
class SaveEntryUseCase(
    private val addNoteUseCase: AddNoteUseCase,
    private val deleteEntryDraft: DeleteEntryDraftUseCase,
) {
    /**
     * Persists [notes] to the given journals and deletes the active draft if present.
     */
    suspend operator fun invoke(
        notes: List<JournalNote>,
        journalIds: List<Uuid>,
        activeDraftId: Uuid?,
    ) {
        addNoteUseCase(notes = notes, journalIds = journalIds)
        if (activeDraftId != null) {
            deleteEntryDraft(activeDraftId)
        }
    }
}
