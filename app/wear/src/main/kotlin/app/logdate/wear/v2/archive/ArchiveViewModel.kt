package app.logdate.wear.v2.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.v2.library.LibraryWindow
import app.logdate.wear.v2.today.DayClock
import app.logdate.wear.v2.today.TodayWindow
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** One day of the Archive: its date and its moments, newest first. */
data class ArchiveDay(
    val date: LocalDate,
    val moments: List<JournalNote>,
)

/** What the Archive shows. */
sealed interface ArchiveUiState {
    data object Loading : ArchiveUiState

    data class Ready(
        val timeZone: TimeZone,
        val days: List<ArchiveDay>,
    ) : ArchiveUiState

    data object Failed : ArchiveUiState
}

/** The library window past the last 7 days, a day at a time, newest day first, moving with the date. */
class ArchiveViewModel(
    dayClock: DayClock,
    notesRepository: JournalNotesRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ArchiveUiState> =
        dayClock
            .observe()
            .flatMapLatest<TodayWindow, ArchiveUiState> { today ->
                val start = LibraryWindow.windowStart(today)
                val end = LibraryWindow.recentStart(today)
                notesRepository.observeNotesInRange(start, end).map { notes ->
                    ArchiveUiState.Ready(timeZone = today.timeZone, days = archiveDays(notes, start, end, today.timeZone))
                }
            }.catch { error ->
                Napier.e("Couldn't load earlier moments on the watch", error)
                emit(ArchiveUiState.Failed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArchiveUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The library moments of [notes] from [start] up to [end], grouped by their day in [timeZone]. */
internal fun archiveDays(
    notes: List<JournalNote>,
    start: Instant,
    end: Instant,
    timeZone: TimeZone,
): List<ArchiveDay> =
    notes
        .filter { it.creationTimestamp >= start && it.creationTimestamp < end }
        .filter(LibraryWindow::isLibraryMoment)
        .sortedByDescending(JournalNote::creationTimestamp)
        .groupBy { it.creationTimestamp.toLocalDateTime(timeZone).date }
        .map { (date, moments) -> ArchiveDay(date, moments) }
