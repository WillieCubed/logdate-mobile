package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for fetching all entry drafts.
 */
class GetAllDraftsUseCase(
    private val entryDraftRepository: EntryDraftRepository
) {
    /**
     * Returns a flow of all entry drafts, sorted by most recently updated first.
     */
    operator fun invoke(): Flow<List<EntryDraft>> {
        return entryDraftRepository.getDrafts()
    }
}