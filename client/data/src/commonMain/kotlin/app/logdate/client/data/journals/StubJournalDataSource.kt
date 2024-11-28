package app.logdate.client.data.journals

import app.logdate.shared.model.Journal

object StubJournalDataSource : RemoteJournalDataSource {
    override suspend fun observeAllJournals(): List<Journal> {
        // no-op
        return emptyList()
    }

    override suspend fun addJournal(journal: Journal): String {
        // no-op
        return ""
    }

    override suspend fun editJournal(journal: Journal) {
        // no-op
    }

    override suspend fun deleteJournal(journalId: String) {
        // no-op
    }
}