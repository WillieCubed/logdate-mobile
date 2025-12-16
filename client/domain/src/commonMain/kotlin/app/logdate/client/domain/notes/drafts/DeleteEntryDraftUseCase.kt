package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraftRepository
import kotlin.uuid.Uuid

/**
 * Use case for deleting entry drafts.
 */
class DeleteEntryDraftUseCase(
    private val entryDraftRepository: EntryDraftRepository,
) {
    /**
     * Deletes the draft with the given ID.
     *
     * @param draftId The ID of the draft to delete
     */
    suspend operator fun invoke(draftId: Uuid) {
        entryDraftRepository.deleteDraft(draftId)
    }
}