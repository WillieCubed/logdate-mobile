package app.logdate.feature.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.domain.timeline.GetTimelineUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.shared.model.Person
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDaySelection
import app.logdate.ui.timeline.TimelineDayUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A view model for the timeline overview screen.
 */
class TimelineViewModel(
    getTimelineUseCase: GetTimelineUseCase,
    notesRepository: JournalNotesRepository, // TODO: Consolidate with GetTimelineUseCase
    private val removeNoteUseCase: RemoveNoteUseCase,
) : ViewModel() {

    private val _selectedItemUiState =
        MutableStateFlow<TimelineDaySelection>(TimelineDaySelection.NotSelected)

    private val selectedNotes = notesRepository.allNotesObserved.map {
        it.filter { note ->
            note.creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date == _selectedItemUiState.value
        }
    }

    val uiState: StateFlow<HomeTimelineUiState> = getTimelineUseCase()
        .combine(selectedNotes) { timeline, notes ->
            HomeTimelineUiState(
                items = timeline.days.map { day ->
                    TimelineDayUiState(
                        summary = day.tldr,
                        date = day.date,
                        people = day.people.map(Person::toUiState),
                        events = day.events,
                        notes = notes.map {
                            when (it) {
                                is JournalNote.Text -> TextNoteUiState(
                                    noteId = it.uid,
                                    text = it.content,
                                    timestamp = it.creationTimestamp,
                                )

                                else -> TODO()
                            }
                        }
                    )
                },
                selectedItem = _selectedItemUiState.value
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeTimelineUiState())

    /**
     * Selects an item timeline so that it can be focused in a companion view.
     */
    fun selectItem(day: LocalDate?) {
        if (day == null) {
            _selectedItemUiState.value = TimelineDaySelection.NotSelected
            return
        }
        _selectedItemUiState.value = TimelineDaySelection.Selected(day.toString(), day)
    }

    /**
     * Removes a note from the use
     */
    fun deleteItem(uid: String) {
        viewModelScope.launch {
            removeNoteUseCase(uid)
            // TODO: Use UI to notify user of deletion
        }
    }
}

