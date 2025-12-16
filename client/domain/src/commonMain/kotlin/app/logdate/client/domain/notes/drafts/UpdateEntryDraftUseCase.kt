package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import kotlin.uuid.Uuid

/**
 * Use case for updating an entry draft.
 */
class UpdateEntryDraftUseCase(
    private val entryDraftRepository: EntryDraftRepository,
) {

    /**
     * Updates an existing draft with new content.
     *
     * @param draftId The UID for the draft to update content
     * @param content The contents of the draft
     * @param overwrite If true, this will replace the draft content (true by default).
     *
     * @return The ID of the updated draft.
     */
    suspend operator fun invoke(
        draftId: Uuid,
        content: List<JournalNote>,
        overwrite: Boolean = true,
    ): Uuid {
        return entryDraftRepository.updateDraft(draftId, content)
    }

    suspend operator fun invoke(
        draftId: Uuid,
        content: EntryDraft,
        overwrite: Boolean = true,
    ): Uuid {
        return invoke(draftId, content.notes, overwrite)
    }
}
