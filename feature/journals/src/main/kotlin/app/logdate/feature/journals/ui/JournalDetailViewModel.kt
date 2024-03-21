package app.logdate.feature.journals.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.JournalRepository
import app.logdate.feature.journals.navigation.JOURNAL_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

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

    fun selectJournal(journalId: String) {
        savedStateHandle["id"] = journalId
    }
}
