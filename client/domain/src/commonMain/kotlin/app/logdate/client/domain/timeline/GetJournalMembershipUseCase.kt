package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Returns an observable map from note ID to the journals each note belongs to.
 */
class GetJournalMembershipUseCase(
    private val journalContentRepository: JournalContentRepository,
) {
    operator fun invoke(noteIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> = journalContentRepository.observeJournalsForContents(noteIds)
}
