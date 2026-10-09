package app.logdate.wear.v2.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.today.MoodNoteTag
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

/** What Home's moment list shows. */
sealed interface FlowUiState {
    data object Loading : FlowUiState

    /** The moments of the last [FlowViewModel.FLOW_DAYS] days, newest first, without check-ins. */
    data class Ready(
        val timeZone: TimeZone,
        val moments: List<JournalNote>,
    ) : FlowUiState

    data object Failed : FlowUiState
}

/** Home's second page: today and the 6 days before it, following the day as it moves. */
class FlowViewModel(
    dayClock: DayClock,
    notesRepository: JournalNotesRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FlowUiState> =
        dayClock
            .observe()
            .flatMapLatest<TodayWindow, FlowUiState> { today ->
                val start = today.date.minus(FLOW_DAYS - 1, DateTimeUnit.DAY).atStartOfDayIn(today.timeZone)
                notesRepository.observeNotesInRange(start, today.end).map { notes ->
                    FlowUiState.Ready(
                        timeZone = today.timeZone,
                        moments =
                            notes
                                .filter { it.creationTimestamp >= start && it.creationTimestamp < today.end }
                                .filterNot(MoodNoteTag::isFeeling)
                                .sortedByDescending(JournalNote::creationTimestamp),
                    )
                }
            }.catch { error ->
                Napier.e("Couldn't load the last 7 days on the watch", error)
                emit(FlowUiState.Failed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FlowUiState.Loading)

    companion object {
        const val FLOW_DAYS = 7
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
