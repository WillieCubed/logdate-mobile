package app.logdate.client.domain.journals

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving journals for the current user.
 */
class GetCurrentUserJournalsUseCase(
    private val journalRepository: JournalRepository,
) {
    /**
     * Returns a flow of journals for the current user.
     *
     * @return A flow emitting lists of journals for the current user.
     */
    operator fun invoke(): Flow<List<Journal>> = journalRepository.allJournalsObserved
}