package app.logdate.client.domain.search

import app.logdate.client.repository.search.SearchRepository
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Orchestrates universal search across all indexed content types.
 *
 * Debouncing is handled by the caller so the UI can represent in-flight search
 * more accurately. Results are ranked by local FTS relevance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UniversalSearchUseCase(
    private val searchRepository: SearchRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    /**
     * Searches all indexed content with optional filtering.
     *
     * Filtering is applied after FTS5 returns results (in-memory). For the date-range filter the
     * window is resolved against [clock] at the moment results are produced, so a long-lived flow
     * keeps using the same window until a new result batch arrives.
     *
     * @param queryFlow Flow of search queries from the UI
     * @param filters Optional filters to restrict result types, date window, or count
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
                        results.applyFilters(filters)
                    }
                }
            }

    private fun List<SearchResult>.applyFilters(filters: SearchFilters): List<SearchResult> {
        val window =
            if (filters.dateRange == DateRangeFilter.AllTime) {
                null
            } else {
                filters.dateRange.window(clock.now(), timeZone())
            }
        if (filters.contentTypes == null && window == null) return this
        return filter { result ->
            (filters.contentTypes == null || result.contentType in filters.contentTypes) &&
                (window == null || (result.created >= window.from && result.created < window.toExclusive))
        }
    }
}
