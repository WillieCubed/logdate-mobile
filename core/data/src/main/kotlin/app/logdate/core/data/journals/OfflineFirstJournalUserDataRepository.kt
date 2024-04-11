package app.logdate.core.data.journals

import app.logdate.core.data.JournalRepository
import javax.inject.Inject

class OfflineFirstJournalUserDataRepository @Inject constructor(
    private val journalRepository: JournalRepository
) : JournalUserDataRepository {

    override suspend fun changeFavoritedStatus(journalId: String, isFavorite: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun changeArchiveStatus(journalId: String, isArchived: Boolean) {
        TODO("Not yet implemented")
    }

}