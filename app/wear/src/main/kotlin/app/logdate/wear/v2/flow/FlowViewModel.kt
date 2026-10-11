package app.logdate.wear.v2.flow

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
import kotlinx.datetime.TimeZone

/** What Home's moment list shows. */
sealed interface FlowUiState {
    data object Loading : FlowUiState

    /**
     * The voice and text moments of the last [LibraryWindow.RECENT_DAYS] days, newest first, and how
     * many more the rest of the library window has.
     */
    data class Ready(
        val timeZone: TimeZone,
        val moments: List<JournalNote>,
        val earlierCount: Int = 0,
    ) : FlowUiState

    data object Failed : FlowUiState
}

/** Home's second page: the library window's last 7 days, following the day as it moves. */
class FlowViewModel(
    dayClock: DayClock,
    notesRepository: JournalNotesRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FlowUiState> =
        dayClock
            .observe()
            .flatMapLatest<TodayWindow, FlowUiState> { today ->
                val windowStart = LibraryWindow.windowStart(today)
                val recentStart = LibraryWindow.recentStart(today)
                notesRepository.observeNotesInRange(windowStart, today.end).map { notes ->
                    val library =
                        notes
                            .filter { it.creationTimestamp >= windowStart && it.creationTimestamp < today.end }
                            .filter(LibraryWindow::isLibraryMoment)
                    FlowUiState.Ready(
                        timeZone = today.timeZone,
                        moments = library.filter { it.creationTimestamp >= recentStart }.sortedByDescending(JournalNote::creationTimestamp),
                        earlierCount = library.count { it.creationTimestamp < recentStart },
                    )
                }
            }.catch { error ->
                Napier.e("Couldn't load the last 7 days on the watch", error)
                emit(FlowUiState.Failed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FlowUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
