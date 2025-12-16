package app.logdate.feature.journals.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.journals.DeleteJournalUseCase
import app.logdate.client.domain.journals.GetJournalByIdUseCase
import app.logdate.client.domain.journals.UpdateJournalUseCase
import app.logdate.client.sharing.SharingLauncher
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * View model for managing journal settings.
 * Provides functions to update journal properties and delete the journal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalSettingsViewModel(
    private val getJournalByIdUseCase: GetJournalByIdUseCase,
    private val updateJournalUseCase: UpdateJournalUseCase,
    private val deleteJournalUseCase: DeleteJournalUseCase,
    private val sharingLauncher: SharingLauncher,
) : ViewModel() {

    private val _journalId = MutableStateFlow<Uuid?>(null)
    
    // State to track the currently edited name
    private val _editedName = MutableStateFlow<String?>(null)
    
    // Combine the journal data with edited name to produce UI state
    val uiState: StateFlow<JournalSettingsUiState> = _journalId
        .filterNotNull()
        .flatMapLatest { id ->
            getJournalByIdUseCase(id).combine(_editedName) { journal, editedName ->
                val nameToUse = editedName ?: journal.title
                val hasChanges = nameToUse != journal.title
                
                JournalSettingsUiState.Loaded(
                    journal = journal,
                    editedName = nameToUse,
                    hasUnsavedChanges = hasChanges
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            JournalSettingsUiState.Unknown
        )
        
    /**
     * Sets the journal ID to load.
     */
    fun setSelectedJournalId(journalId: Uuid) {
        _journalId.value = journalId
    }

    /**
     * Shares the journal using the platform-specific sharing launcher.
     * Launches a system share sheet with the journal link.
     */
    fun shareJournal() {
        val journalId = _journalId.value ?: return
        
        try {
            sharingLauncher.shareJournalLink(journalId)
        } catch (e: Exception) {
            Napier.e("Failed to share journal", e)
        }
    }

    /**
     * Updates the journal name in local state without saving to repository.
     * This tracks the changes in the UI but doesn't persist them.
     * 
     * @param newName The new name for the journal.
     */
    fun updateJournalName(newName: String) {
        _editedName.value = newName
    }
    
    /**
     * Saves the current journal name using the update use case.
     * 
     * @param onSuccess Callback to be invoked when the journal is successfully updated
     */
    fun saveJournalChanges(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val journalId = _journalId.value ?: return@launch
            val currentState = uiState.value
            
            // Only save if we have unsaved changes
            if (currentState is JournalSettingsUiState.Loaded && currentState.hasUnsavedChanges) {
                // Get the current journal and update its name
                val updatedJournal = currentState.journal.copy(
                    title = currentState.editedName
                )
                
                // Save using use case
                val success = updateJournalUseCase(updatedJournal)
                
                if (success) {
                    // Reset edited name to null so it will use the new title from the repository
                    _editedName.value = null
                    onSuccess()
                } else {
                    Napier.e("Failed to update journal name")
                }
            } else {
                // If no changes, still call success
                onSuccess()
            }
        }
    }
    
    /**
     * Deletes the current journal.
     * 
     * @param onSuccess Callback to be invoked when the journal is successfully deleted.
     */
    fun deleteJournal(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _journalId.value?.let { id ->
                val success = deleteJournalUseCase(id)
                
                if (success) {
                    onSuccess()
                } else {
                    Napier.e("Failed to delete journal")
                }
            }
        }
    }
}

/**
 * UI state for the journal settings screen.
 */
sealed class JournalSettingsUiState {
    /**
     * State when the journal is loaded and ready for editing
     * 
     * @param journal The journal being edited
     * @param editedName Current value of the journal name field (may be different from journal.title)
     * @param hasUnsavedChanges Whether there are unsaved changes to the journal
     */
    data class Loaded(
        val journal: Journal,
        val editedName: String = journal.title,
        val hasUnsavedChanges: Boolean = false
    ) : JournalSettingsUiState()

    data object Unknown : JournalSettingsUiState()
}