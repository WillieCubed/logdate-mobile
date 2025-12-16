package app.logdate.client.domain.journals

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Use case to get a journal by its ID.
 * 
 * @param repository The repository for journal operations
 */
class GetJournalByIdUseCase(
    private val repository: JournalRepository
) {
    /**
     * Gets a journal by its ID as a flow.
     * 
     * @param journalId The ID of the journal to get
     * @return A flow emitting the journal
     */
    operator fun invoke(journalId: Uuid): Flow<Journal> {
        return repository.observeJournalById(journalId)
    }
}