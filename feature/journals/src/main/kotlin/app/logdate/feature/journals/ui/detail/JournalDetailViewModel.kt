package app.logdate.feature.journals.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.JournalRepository
import app.logdate.core.sharing.SharingLauncher
import app.logdate.feature.journals.navigation.JOURNAL_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val sharingLauncher: SharingLauncher,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<JournalDetailUiState> =
        savedStateHandle.getStateFlow<String?>(JOURNAL_ID_ARG, null)
            .filterNotNull()
            .flatMapLatest { id ->
                repository.observeJournalById(id)
            }
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
        val journalId = savedStateHandle.get<String>(JOURNAL_ID_ARG) ?: return
        sharingLauncher.shareJournalToInstagram(journalId)
    }

    fun deleteJournal(onDelete: () -> Unit) {
        val journalId = savedStateHandle.get<String>(JOURNAL_ID_ARG) ?: return
        viewModelScope.launch {
            repository.delete(journalId)
        }
        onDelete()
    }
}
