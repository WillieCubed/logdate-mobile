package app.logdate.wear.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class WearHomeViewModel(
    notesRepository: JournalNotesRepository,
) : ViewModel() {

    private val today: LocalDate
        get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    val uiState = notesRepository
        .observeNotesForDay(today)
        .map { notes ->
            WearHomeUiState(
                timeOfDay = currentTimeOfDay(),
                entryCount = notes.size,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WearHomeUiState(timeOfDay = currentTimeOfDay()),
        )

    private fun currentTimeOfDay(): TimeOfDay {
        val hour = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .hour
        return when {
            hour < 12 -> TimeOfDay.MORNING
            hour < 17 -> TimeOfDay.AFTERNOON
            else -> TimeOfDay.EVENING
        }
    }
}

enum class TimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
}

data class WearHomeUiState(
    val timeOfDay: TimeOfDay = TimeOfDay.MORNING,
    val entryCount: Int = 0,
)
