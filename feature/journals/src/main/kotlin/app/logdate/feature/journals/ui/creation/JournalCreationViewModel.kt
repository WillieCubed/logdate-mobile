package app.logdate.feature.journals.ui.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.JournalRepository
import app.logdate.model.Journal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

@HiltViewModel
class JournalCreationViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
) : ViewModel() {
    private val backingUiSate = MutableStateFlow(JournalCreationUiState())
    val uiState: StateFlow<JournalCreationUiState> = backingUiSate

    fun createJournal(data: NewJournalRequest) {
        viewModelScope.launch {
            val id = journalRepository.create(
                Journal(
                    title = data.title,
                    created = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    description = data.contentDescription,
                    id = "",
                    isFavorited = false,
                )
            )
            backingUiSate.update { currentState ->
                currentState.copy(created = true, journalId = id)
            }
        }
    }
}

data class JournalCreationUiState(
    val created: Boolean = false,
    val journalId: String = "",
)