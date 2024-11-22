package app.logdate.feature.journals.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.logdate.core.data.journals.JournalRepository
import app.logdate.core.sharing.SharingLauncher
import app.logdate.model.Journal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class JournalSettingsViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val sharingLauncher: SharingLauncher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val journalId = savedStateHandle.getStateFlow(NavController.KEY_DEEP_LINK_INTENT, "")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<JournalSettingsUiState> =
        journalId
            .flatMapLatest {
                repository.observeJournalById(it)
            }
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