package app.logdate.feature.journals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.journals.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JournalsViewModel @Inject constructor(
    private val repository: JournalRepository,
) : ViewModel() {

    // TODO: Use savedStateHandle to create filters

    val uiState: StateFlow<JournalsUiState> = repository
        .allJournalsObserved
        .map {
            JournalsUiState.Success(
                journals = it
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalsUiState.Loading)

    fun removeJournal(journalId: String) {
        viewModelScope.launch {
            repository.delete(journalId)
        }
    }

    fun addJournal() = viewModelScope.launch {
//            repository.create()
    }
}
