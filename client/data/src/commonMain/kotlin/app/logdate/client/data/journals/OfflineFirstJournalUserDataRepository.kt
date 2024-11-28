package app.logdate.client.data.journals

import app.logdate.client.repository.journals.JournalRepository

class OfflineFirstJournalUserDataRepository(
    private val journalRepository: JournalRepository,
) : JournalUserDataRepository {

    override suspend fun changeFavoritedStatus(journalId: String, isFavorite: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun changeArchiveStatus(journalId: String, isArchived: Boolean) {
        TODO("Not yet implemented")
    }

}