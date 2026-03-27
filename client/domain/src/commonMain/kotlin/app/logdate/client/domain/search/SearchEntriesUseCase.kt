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
 * Use case for searching across all journal entries.
 *
 * Provides debounced search to avoid excessive queries while the user types.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchEntriesUseCase(
    private val searchRepository: SearchRepository,
) {
    /**
     * Search delay in milliseconds to avoid excessive queries while typing.
     */
    private val searchDebounceMs = 300L

    /**
     * Searches all entries with debouncing.
     *
     * The search is debounced by 300ms to avoid excessive queries while the user types.
     * Returns empty list if query is blank.
     *
     * @param queryFlow Flow of search queries from the UI
     * @return Flow of search results
     */
    operator fun invoke(queryFlow: Flow<SearchQuery>): Flow<List<SearchResult>> =
        queryFlow
            .map { it.text }
            .debounce(searchDebounceMs)
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
            .debounce(searchDebounceMs)
            .distinctUntilChanged()
            .flatMapLatest { queryText ->
                if (queryText.isBlank()) {
                    flowOf(emptyList())
                } else {
                    searchRepository.searchWithLimit(queryText, limit)
                }
            }
}
