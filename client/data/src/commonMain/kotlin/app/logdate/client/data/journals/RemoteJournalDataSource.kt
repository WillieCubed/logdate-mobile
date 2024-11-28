package app.logdate.client.data.journals

import app.logdate.shared.model.Journal

interface RemoteJournalDataSource {
    suspend fun observeAllJournals(): List<Journal>

    suspend fun addJournal(journal: Journal): String

    suspend fun editJournal(journal: Journal)

    suspend fun deleteJournal(journalId: String)
}