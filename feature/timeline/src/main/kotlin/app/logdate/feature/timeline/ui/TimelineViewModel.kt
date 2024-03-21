package app.logdate.feature.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.timeline.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TimelineViewModel @Inject constructor(
    timelineRepository: TimelineRepository,
) : ViewModel() {

    val uiState: StateFlow<TimelineUiState> = timelineRepository
        .allItemsObserved
        .map {
            TimelineUiState.Success(items = it)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineUiState.Loading)
}
