package app.logdate.feature.journals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class JournalsOverviewViewModel(
    private val repository: JournalRepository,
) : ViewModel() {

    // TODO: Use savedStateHandle to create filters

    val uiState: StateFlow<JournalsOverviewUiState> = repository
        .allJournalsObserved
        .map { journals ->
            val journalItems = journals.map { journal ->
                JournalListItemUiState.ExistingJournal(journal)
            }
            val allItems = journalItems + JournalListItemUiState.CreateJournalPlaceholder
            
            JournalsOverviewUiState(journals = allItems)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalsOverviewUiState())

    fun removeJournal(journalId: Uuid) {
        viewModelScope.launch {
            repository.delete(journalId)
        }
    }

    fun addJournal() = viewModelScope.launch {
//            repository.create()
    }
}
