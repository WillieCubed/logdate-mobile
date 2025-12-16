package app.logdate.feature.journals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * ViewModel for the journal detail screen.
 */
class JournalDetailViewModel(
    private val journalId: String,
    private val journalRepository: JournalRepository,
    private val journalContentRepository: JournalContentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<JournalDetailUiState>(JournalDetailUiState.Loading)
    
    // Combine journal details with notes
    val uiState: StateFlow<JournalDetailUiState> = 
        journalRepository.observeJournalById(Uuid.parse(journalId))
            .combine(journalContentRepository.observeContentForJournal(Uuid.parse(journalId))) { journal, notes ->
                JournalDetailUiState.Success(journal, notes)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                JournalDetailUiState.Loading
            )
            
    init {
        loadJournalDetails()
    }
    
    private fun loadJournalDetails() {
        viewModelScope.launch {
            try {
                // Initial load is handled by the StateFlow
            } catch (e: Exception) {
                _uiState.value = JournalDetailUiState.Error("Failed to load journal: ${e.message}")
            }
        }
    }
}

/**
 * UI state for the journal detail screen.
 */
sealed interface JournalDetailUiState {
    data object Loading : JournalDetailUiState
    
    data class Success(
        val journal: Journal,
        val notes: List<JournalNote>
    ) : JournalDetailUiState
    
    data class Error(val message: String) : JournalDetailUiState
}