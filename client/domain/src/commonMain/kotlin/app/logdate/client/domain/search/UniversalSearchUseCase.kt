package app.logdate.client.domain.search

import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Orchestrates universal search across all indexed content types.
 *
 * Wraps the search repository with debouncing, query sanitization, and
 * optional content type filtering. Results are ranked by FTS5 relevance.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class UniversalSearchUseCase(
    private val searchRepository: SearchRepository,
) {
    private val searchDebounceMs = 300L

    /**
     * Searches all indexed content with debouncing and filtering.
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
            .debounce(searchDebounceMs)
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
