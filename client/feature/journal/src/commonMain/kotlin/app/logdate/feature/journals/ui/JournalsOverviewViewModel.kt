package app.logdate.feature.journals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * ViewModel for the journals overview screen.
 *
 * Combines journals from the repository with user-selected layout mode, sort order, and
 * filters. Layout mode is persisted via DataStore; sort and filter are session-scoped.
 */
class JournalsOverviewViewModel(
    private val repository: JournalRepository,
    private val preferencesDataSource: LogdatePreferencesDataSource,
) : ViewModel() {
    private val sortOptionState = MutableStateFlow(JournalSortOption.LAST_UPDATED)
    private val activeFiltersState = MutableStateFlow<Set<JournalFilter>>(emptySet())

    private val layoutModeFlow =
        preferencesDataSource.observeJournalLayoutMode().map { name ->
            try {
                JournalLayoutMode.valueOf(name)
            } catch (_: IllegalArgumentException) {
                JournalLayoutMode.CAROUSEL
            }
        }

    val uiState: StateFlow<JournalsOverviewUiState> =
        combine(
            repository.allJournalsObserved,
            layoutModeFlow,
            sortOptionState,
            activeFiltersState,
        ) { journals, layoutMode, sortOption, filters ->
            val sorted = applySorting(journals, sortOption)
            val items =
                sorted.map { journal ->
                    JournalListItemUiState.ExistingJournal(journal)
                } + JournalListItemUiState.CreateJournalPlaceholder

            JournalsOverviewUiState(
                journals = items,
                layoutMode = layoutMode,
                sortOption = sortOption,
                activeFilters = filters,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalsOverviewUiState())

    fun toggleLayoutMode() {
        viewModelScope.launch {
            val current = uiState.value.layoutMode
            val next =
                when (current) {
                    JournalLayoutMode.GRID -> JournalLayoutMode.CAROUSEL
                    JournalLayoutMode.CAROUSEL -> JournalLayoutMode.GRID
                }
            preferencesDataSource.setJournalLayoutMode(next.name)
        }
    }

    fun setSortOption(option: JournalSortOption) {
        sortOptionState.value = option
    }

    fun toggleFilter(filter: JournalFilter) {
        activeFiltersState.update { current ->
            if (filter in current) current - filter else current + filter
        }
    }

    fun removeJournal(journalId: Uuid) {
        viewModelScope.launch {
            repository.delete(journalId)
        }
    }

    private fun applySorting(
        journals: List<Journal>,
        sortOption: JournalSortOption,
    ): List<Journal> =
        when (sortOption) {
            JournalSortOption.LAST_UPDATED -> journals.sortedByDescending { it.lastUpdated }
            JournalSortOption.CREATED -> journals.sortedByDescending { it.created }
            JournalSortOption.TITLE -> journals.sortedBy { it.title }
        }
}
