package app.logdate.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.search.ObserveRecentSearchesUseCase
import app.logdate.client.domain.search.SearchQuery
import app.logdate.client.domain.search.UniversalSearchUseCase
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the universal search screen.
 *
 * Manages search query state, recent searches, and ranked results across
 * all indexed content types.
 */
class SearchViewModel(
    universalSearchUseCase: UniversalSearchUseCase,
    private val observeRecentSearchesUseCase: ObserveRecentSearchesUseCase,
) : ViewModel() {
    private val queryState = MutableStateFlow(SearchQuery.Empty)

    /**
     * The current search screen state: idle (with recents), empty, or results.
     */
    val searchState: StateFlow<SearchScreenState> =
        combine(
            queryState,
            universalSearchUseCase(queryState),
            observeRecentSearchesUseCase(),
        ) { query, results, recentSearches ->
            when {
                query.isBlank -> SearchScreenState.Idle(recentSearches)
                results.isEmpty() -> SearchScreenState.Empty(query.text)
                else -> SearchScreenState.Results(results)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchScreenState.Idle(emptyList()),
        )

    /**
     * Updates the search query text.
     */
    fun updateQuery(newQuery: String) {
        queryState.update { SearchQuery(newQuery) }
    }

    /**
     * Records the current query as a recent search (call when user commits a search).
     */
    fun commitSearch() {
        val current = queryState.value
        if (current.isNotEmpty) {
            viewModelScope.launch {
                observeRecentSearchesUseCase.record(current.text)
            }
        }
    }

    /**
     * Clears the current search query.
     */
    fun clearSearch() {
        queryState.update { SearchQuery.Empty }
    }
}

/**
 * UI state for the search screen.
 */
sealed interface SearchScreenState {
    /**
     * No active query. Show recent searches as suggestions.
     */
    data class Idle(
        val recentSearches: List<String>,
    ) : SearchScreenState

    /**
     * Query submitted but no results found.
     */
    data class Empty(
        val query: String,
    ) : SearchScreenState

    /**
     * Ranked results from universal search.
     */
    data class Results(
        val results: List<SearchResult>,
    ) : SearchScreenState
}
