package app.logdate.client.data.fakes

import app.logdate.client.data.journals.RemoteJournalDataSource
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

/**
 * Fake implementation of [RemoteJournalDataSource] for testing.
 */
class FakeRemoteJournalDataSource : RemoteJournalDataSource {
    private val journals = mutableMapOf<String, Journal>()

    override suspend fun observeAllJournals(): List<Journal> {
        return journals.values.toList()
    }

    override suspend fun addJournal(journal: Journal): String {
        journals[journal.id.toString()] = journal
        return journal.id.toString()
    }

    override suspend fun editJournal(journal: Journal) {
        journals[journal.id.toString()] = journal
    }

    override suspend fun deleteJournal(journalId: String) {
        journals.remove(journalId)
    }
    
    /**
     * Adds a journal directly to the remote source for testing purposes.
     * 
     * @param journal The journal to add
     */
    fun addJournalForTesting(journal: Journal) {
        journals[journal.id.toString()] = journal
    }
    
    /**
     * Clears all journals in the fake remote source.
     * This method is specific to the fake implementation for testing.
     */
    fun clear() {
        journals.clear()
    }
}