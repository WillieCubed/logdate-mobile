package app.logdate.feature.rewind.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.rewind.RewindRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RewindOverviewViewModel @Inject constructor(
    repository: RewindRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RewindUiState.Loading)

    val uiState: StateFlow<RewindUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeMostRecentRewind()
                .collect {
                    RewindUiState.Loaded(
                        ready = false,
                        data = RewindData(
                            title = it.title,
                            label = it.label,
                            issueDate = it.date,
                            media = listOf(),
                            people = listOf(),
                            places = listOf(),
                        )
                    )
                }
        }
    }
}
