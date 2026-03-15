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
                greeting = greetingForTimeOfDay(),
                entryCount = notes.size,
                entryCountLabel = when (notes.size) {
                    0 -> "No entries yet"
                    1 -> "1 entry today"
                    else -> "${notes.size} entries today"
                },
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WearHomeUiState(greeting = greetingForTimeOfDay()),
        )

    private fun greetingForTimeOfDay(): String {
        val hour = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .hour
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}

data class WearHomeUiState(
    val greeting: String = "",
    val entryCount: Int = 0,
    val entryCountLabel: String = "No entries yet",
)
