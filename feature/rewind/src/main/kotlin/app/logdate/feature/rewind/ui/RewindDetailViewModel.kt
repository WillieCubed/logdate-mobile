package app.logdate.feature.rewind.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.JournalRepository
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
class RewindDetailViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val uiState: StateFlow<RewindDetailUiState> =
        savedStateHandle.getStateFlow<String?>("id", null)
            .filterNotNull()
            .flatMapLatest { id ->
                repository.observeJournalById(id)
            }
            .catch {
                when (it) {
                    is IllegalStateException -> {
                        RewindDetailUiState.Error(
                            "unknown",
                            "Could not load this journal. Try again later."
                        )
                    }

                    else -> {
                        RewindDetailUiState.Error(
                            "journal-not-exist",
                            "Could not load this journal. It has an unknown or invalid journal ID."
                        )
                    }
                }
            }
            .map {
                RewindDetailUiState.Success(
                    listOf() // TODO: Replace with actual contents
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                RewindDetailUiState.Loading
            )

    fun selectJournal(journalId: String) {
        savedStateHandle["id"] = journalId
    }
}
