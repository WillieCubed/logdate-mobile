package app.logdate.feature.journals.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sharing.SharingLauncher
import app.logdate.feature.journals.navigation.JournalDetailsRoute
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class JournalSettingsViewModel(
    private val repository: JournalRepository,
    private val sharingLauncher: SharingLauncher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // TODO: Consider separate route for journal settings
    private val journalData = savedStateHandle.toRoute<JournalDetailsRoute>()

    val uiState: StateFlow<JournalSettingsUiState> =
        repository.observeJournalById(journalData.journalId)
            .map { JournalSettingsUiState.Loaded(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                JournalSettingsUiState.Unknown,
            )

    fun shareJournal() {

    }
}

sealed class JournalSettingsUiState(
) {
    data class Loaded(
        val journal: Journal,
    ) : JournalSettingsUiState()

    data object Unknown : JournalSettingsUiState()
}