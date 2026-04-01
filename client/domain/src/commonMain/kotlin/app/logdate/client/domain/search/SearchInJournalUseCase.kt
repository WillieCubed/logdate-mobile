package app.logdate.client.domain.search

import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Searches entries within a specific journal using full-text search.
 */
class SearchInJournalUseCase(
    private val searchRepository: SearchRepository,
) {
    operator fun invoke(
        query: String,
        journalId: Uuid,
        limit: Int = 50,
    ): Flow<List<SearchResult>> = searchRepository.searchInJournal(query, journalId, limit)
}
