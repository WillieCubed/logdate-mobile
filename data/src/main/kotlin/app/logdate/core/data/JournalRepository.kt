package app.logdate.core.data

import app.logdate.model.Journal
import kotlinx.coroutines.flow.Flow

interface JournalRepository {
    val allJournalsObserved: Flow<List<Journal>>

    fun observeJournalById(id: String): Flow<Journal>

    suspend fun create(journal: Journal)

    suspend fun delete(journalId: String)
}
