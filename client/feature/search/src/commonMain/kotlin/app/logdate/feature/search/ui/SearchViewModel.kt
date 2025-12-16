package app.logdate.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.search.SearchEntriesUseCase
import app.logdate.client.repository.search.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * ViewModel for the search screen.
 *
 * Manages search query state and search results with debouncing.
 */
class SearchViewModel(
    searchEntriesUseCase: SearchEntriesUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Search results from the use case, automatically updated when query changes.
     * The use case handles debouncing internally.
     */
    val searchResults: StateFlow<List<SearchResult>> = searchEntriesUseCase(_query)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Updates the search query.
     *
     * This will trigger a new search after the debounce period.
     */
    fun updateQuery(newQuery: String) {
        _query.update { newQuery }
    }

    /**
     * Clears the search query and results.
     */
    fun clearSearch() {
        _query.update { "" }
    }
}
