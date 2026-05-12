package app.logdate.client.data.journals

import app.logdate.client.repository.journals.JournalRepository
import kotlin.uuid.Uuid

class OfflineFirstJournalUserDataRepository(
    private val journalRepository: JournalRepository,
) : JournalUserDataRepository {
    override suspend fun changeFavoritedStatus(
        journalId: String,
        isFavorite: Boolean,
    ) {
        val id = Uuid.parse(journalId)
        val journal = journalRepository.getJournalById(id) ?: return
        journalRepository.update(journal.copy(isFavorited = isFavorite))
    }

    override suspend fun changeArchiveStatus(
        journalId: String,
        isArchived: Boolean,
    ) {
        val id = Uuid.parse(journalId)
        if (!isArchived || journalRepository.getJournalById(id) == null) return
    }
}
