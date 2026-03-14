package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraftRepository

/**
 * Use case for deleting all entry drafts at once.
 */
class DeleteAllDraftsUseCase(
    private val entryDraftRepository: EntryDraftRepository,
) {
    suspend operator fun invoke() {
        entryDraftRepository.deleteAllDrafts()
    }
}
