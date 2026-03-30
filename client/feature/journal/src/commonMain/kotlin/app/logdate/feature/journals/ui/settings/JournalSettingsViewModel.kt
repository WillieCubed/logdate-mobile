@file:Suppress("ktlint:standard:function-naming")

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
    private val journalIdState = MutableStateFlow<Uuid?>(null)

    private val editedNameState = MutableStateFlow<String?>(null)
    private val editedDescriptionState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<JournalSettingsUiState> =
        journalIdState
            .filterNotNull()
            .flatMapLatest { id ->
                getJournalByIdUseCase(id)
                    .combine(editedNameState) { journal, editedName -> journal to editedName }
                    .combine(editedDescriptionState) { (journal, editedName), editedDesc ->
                        val nameToUse = editedName ?: journal.title
                        val descToUse = editedDesc ?: journal.description
                        val hasChanges = nameToUse != journal.title || descToUse != journal.description

                        JournalSettingsUiState.Loaded(
                            journal = journal,
                            editedName = nameToUse,
                            editedDescription = descToUse,
                            hasUnsavedChanges = hasChanges,
                        )
                    }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                JournalSettingsUiState.Unknown,
            )

    /**
     * Sets the journal ID to load.
     */
    fun setSelectedJournalId(journalId: Uuid) {
        journalIdState.value = journalId
    }

    /**
     * Shares the journal using the platform-specific sharing launcher.
     * Launches a system share sheet with the journal link.
     */
    fun shareJournal() {
        val journalId = journalIdState.value ?: return

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
        editedNameState.value = newName
    }

    fun updateJournalDescription(newDescription: String) {
        editedDescriptionState.value = newDescription
    }

    /**
     * Saves the current journal name using the update use case.
     *
     * @param onSuccess Callback to be invoked when the journal is successfully updated
     */
    fun saveJournalChanges(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val journalId = journalIdState.value ?: return@launch
            val currentState = uiState.value

            // Only save if we have unsaved changes
            if (currentState is JournalSettingsUiState.Loaded && currentState.hasUnsavedChanges) {
                // Get the current journal and update its name
                val updatedJournal =
                    currentState.journal.copy(
                        title = currentState.editedName,
                        description = currentState.editedDescription,
                    )

                val success = updateJournalUseCase(updatedJournal)

                if (success) {
                    editedNameState.value = null
                    editedDescriptionState.value = null
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
            journalIdState.value?.let { id ->
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
        val editedDescription: String = journal.description,
        val hasUnsavedChanges: Boolean = false,
    ) : JournalSettingsUiState()

    data object Unknown : JournalSettingsUiState()
}
