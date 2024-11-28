package app.logdate.feature.journals.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sharing.SharingLauncher
import app.logdate.feature.journals.navigation.JournalDetailsRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for interacting with journal details
 */
class JournalDetailViewModel(
    private val repository: JournalRepository,
    private val sharingLauncher: SharingLauncher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<JournalDetailsRoute>()

    val uiState: StateFlow<JournalDetailUiState> = repository.observeJournalById(route.journalId)
        .catch {
            when (it) {
                is IllegalStateException -> {
                    JournalDetailUiState.Error(
                        "unknown",
                        "Could not load this journal. Try again later."
                    )
                }

                else -> {
                    JournalDetailUiState.Error(
                        "journal-not-exist",
                        "Could not load this journal. It has an unknown or invalid journal ID."
                    )
                }
            }
        }
        .map {
            JournalDetailUiState.Success(
                it.title,
                listOf() // TODO: Replace with actual contents
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            JournalDetailUiState.Loading
        )

    /**
     * Shares the current journal.
     */
    fun shareCurrentJournal() {
        val journalId = route.journalId
        viewModelScope.launch {
            sharingLauncher.shareJournalToInstagram(journalId)
        }
    }

    fun deleteJournal(onDelete: () -> Unit) {
        val journalId = route.journalId
        viewModelScope.launch {
            repository.delete(journalId)
            onDelete()
        }
    }
}
