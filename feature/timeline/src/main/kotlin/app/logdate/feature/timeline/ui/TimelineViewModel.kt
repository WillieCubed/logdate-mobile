package app.logdate.feature.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.notes.JournalNotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * A view model for the timeline overview screen.
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    // TODO: Figure out use for TimelineRepository
//    timelineRepository: TimelineRepository,
    notesRepository: JournalNotesRepository,
) : ViewModel() {

    val uiState: StateFlow<TimelineUiState> = notesRepository.allNotesObserved
        .map {
            TimelineUiState.Success(items = it)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineUiState.Loading)
}
