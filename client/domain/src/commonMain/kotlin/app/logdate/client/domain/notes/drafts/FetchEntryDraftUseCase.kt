package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Use case for fetching an entry draft.
 */
class FetchEntryDraftUseCase(
    private val entryDraftRepository: EntryDraftRepository
) {
    /**
     * Fetches a specific draft.
     *
     * If no draft with the given UID exists, the returned flow will emit a failure result.
     */
    operator fun invoke(id: Uuid): Flow<Result<EntryDraft>> {
        val result = entryDraftRepository.getDraft(id)
        return result
    }
}
