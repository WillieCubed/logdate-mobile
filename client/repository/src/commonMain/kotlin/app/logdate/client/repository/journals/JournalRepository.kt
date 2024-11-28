package app.logdate.client.repository.journals

import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow

interface JournalRepository {
    val allJournalsObserved: Flow<List<Journal>>

    fun observeJournalById(id: String): Flow<Journal>

    /**
     * Creates a new journal.
     *
     * @return The ID of the created journal.
     */
    suspend fun create(journal: Journal): String

    suspend fun delete(journalId: String)
}
