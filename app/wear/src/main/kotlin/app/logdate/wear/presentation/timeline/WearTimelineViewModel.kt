package app.logdate.wear.presentation.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class WearTimelineDayUiState(
    val date: LocalDate,
    val entryCount: Int,
    val latestMood: String? = null,
    val previewText: String? = null,
)

data class WearTimelineUiState(
    val days: List<WearTimelineDayUiState> = emptyList(),
    val isLoading: Boolean = false,
)

data class WearDayDetailUiState(
    val date: LocalDate,
    val entries: List<JournalNote>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WearTimelineViewModel(
    private val notesRepository: JournalNotesRepository,
) : ViewModel() {

    val uiState: StateFlow<WearTimelineUiState> =
        notesRepository.observeRecentNotes()
            .map { notes -> groupNotesIntoDays(notes) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, WearTimelineUiState())

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)

    val selectedDayState: StateFlow<WearDayDetailUiState?> =
        _selectedDate
            .flatMapLatest { date ->
                if (date == null) {
                    flowOf(null)
                } else {
                    notesRepository.observeNotesForDay(date).map { notes ->
                        WearDayDetailUiState(date = date, entries = notes)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectDay(date: LocalDate) {
        _selectedDate.value = date
    }

    fun clearSelection() {
        _selectedDate.value = null
    }

    private fun groupNotesIntoDays(notes: List<JournalNote>): WearTimelineUiState {
        if (notes.isEmpty()) {
            return WearTimelineUiState(days = emptyList(), isLoading = false)
        }

        val timezone = TimeZone.currentSystemDefault()
        val grouped = notes.groupBy { note ->
            note.creationTimestamp.toLocalDateTime(timezone).date
        }

        val days = grouped
            .map { (date, dayNotes) ->
                WearTimelineDayUiState(
                    date = date,
                    entryCount = dayNotes.size,
                    latestMood = extractMood(dayNotes),
                    previewText = extractPreview(dayNotes),
                )
            }
            .sortedByDescending { it.date }

        return WearTimelineUiState(days = days, isLoading = false)
    }

    private fun extractMood(notes: List<JournalNote>): String? {
        val moodPattern = Regex("^#mood:(\\w+)")
        for (note in notes) {
            if (note is JournalNote.Text) {
                val match = moodPattern.find(note.content)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        }
        return null
    }

    private fun extractPreview(notes: List<JournalNote>): String? {
        val firstText = notes.firstOrNull { it is JournalNote.Text } as? JournalNote.Text
            ?: return null
        return firstText.content.take(50)
    }
}
