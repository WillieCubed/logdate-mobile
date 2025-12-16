package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case for fetching the most recent entry draft.
 * 
 * This is currently the primary method for loading drafts since we only support
 * a single draft per user. In the future, we'll support multiple drafts.
 */
class FetchMostRecentDraftUseCase(
    private val entryDraftRepository: EntryDraftRepository
) {
    /**
     * Fetches the most recent draft, or null if there are no drafts.
     */
    operator fun invoke(): Flow<EntryDraft?> {
        return entryDraftRepository.getDrafts()
            .map { drafts -> 
                // Find the most recent draft by updatedAt timestamp
                drafts.maxByOrNull { it.updatedAt }
            }
    }
}