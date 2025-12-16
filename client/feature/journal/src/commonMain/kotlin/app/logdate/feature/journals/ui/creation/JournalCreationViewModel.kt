package app.logdate.feature.journals.ui.creation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.feature.journals.navigation.JournalCreationRoute
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class JournalCreationViewModel(
    private val journalRepository: JournalRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val routeData = savedStateHandle.toRoute<JournalCreationRoute>()

    private val backingUiSate = MutableStateFlow(JournalCreationUiState(
        title = routeData.journalTitle
    ))
    val uiState: StateFlow<JournalCreationUiState> = backingUiSate

    fun createJournal(data: NewJournalRequest) {
        viewModelScope.launch {
            val id = journalRepository.create(
                Journal(
                    title = data.title,
                    description = data.contentDescription,
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
    val journalId: kotlin.uuid.Uuid? = null,
    val title: String = "",
)