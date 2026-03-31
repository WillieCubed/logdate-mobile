package app.logdate.client.domain.search

import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Orchestrates universal search across all indexed content types.
 *
 * Debouncing is handled by the caller so the UI can represent in-flight search
 * more accurately. Results are ranked by local FTS relevance.
 */
class UniversalSearchUseCase(
    private val searchRepository: SearchRepository,
) {
    /**
     * Searches all indexed content with optional filtering.
     *
     * @param queryFlow Flow of search queries from the UI
     * @param filters Optional filters to restrict result types or count
     * @return Flow of ranked search results across all entity types
     */
    operator fun invoke(
        queryFlow: Flow<SearchQuery>,
        filters: SearchFilters = SearchFilters.Default,
    ): Flow<List<SearchResult>> =
        queryFlow
            .map { it.text }
            .distinctUntilChanged()
            .flatMapLatest { queryText ->
                if (queryText.isBlank()) {
                    flowOf(emptyList())
                } else {
                    searchRepository.searchRanked(queryText, filters.maxResults).map { results ->
                        if (filters.contentTypes != null) {
                            results.filter { it.contentType in filters.contentTypes }
                        } else {
                            results
                        }
                    }
                }
            }
}
