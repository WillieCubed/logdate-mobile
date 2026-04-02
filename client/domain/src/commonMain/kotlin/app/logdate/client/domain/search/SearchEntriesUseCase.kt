package app.logdate.client.domain.search

import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Use case for searching across all journal entries.
 *
 * Debouncing is handled by the caller so UI layers can expose a precise
 * loading state while the query settles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchEntriesUseCase(
    private val searchRepository: SearchRepository,
) {
    /**
     * Searches all entries for the current query.
     *
     * @param queryFlow Flow of search queries from the UI
     * @return Flow of search results
     */
    operator fun invoke(queryFlow: Flow<SearchQuery>): Flow<List<SearchResult>> =
        queryFlow
            .map { it.text }
            .distinctUntilChanged()
            .flatMapLatest { queryText ->
                if (queryText.isBlank()) {
                    flowOf(emptyList())
                } else {
                    searchRepository.searchWithSnippets(queryText)
                }
            }

    /**
     * Searches with a limit on results (useful for autocomplete/suggestions).
     *
     * @param queryFlow Flow of search queries
     * @param limit Maximum number of results
     * @return Flow of limited search results
     */
    fun searchWithLimit(
        queryFlow: Flow<SearchQuery>,
        limit: Int,
    ): Flow<List<SearchResult>> =
        queryFlow
            .map { it.text }
            .distinctUntilChanged()
            .flatMapLatest { queryText ->
                if (queryText.isBlank()) {
                    flowOf(emptyList())
                } else {
                    searchRepository.searchWithLimit(queryText, limit)
                }
            }
}
