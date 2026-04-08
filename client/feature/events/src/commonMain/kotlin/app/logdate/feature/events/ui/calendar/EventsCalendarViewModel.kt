package app.logdate.feature.events.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.events.ObserveEventsForMonthUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Drives the events calendar surface — a month grid showing inferred and imported events
 * grouped by day. The user can swipe between months and tap a day to see its event list.
 *
 * Pulls events from [ObserveEventsForMonthUseCase] reactively, so the grid updates
 * whenever the inference worker or calendar import worker materializes a new event.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventsCalendarViewModel(
    private val observeEventsForMonth: ObserveEventsForMonthUseCase,
    private val clock: () -> Instant = { Clock.System.now() },
) : ViewModel() {
    private val today: LocalDate = clock().let { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    private val displayedMonthStart = MutableStateFlow(today.startOfMonth())
    private val selectedDay = MutableStateFlow<LocalDate?>(today)

    val uiState: StateFlow<EventsCalendarUiState> =
        combine(
            displayedMonthStart.flatMapLatest { observeEventsForMonth(it) },
            displayedMonthStart,
            selectedDay,
        ) { eventsByDay, monthStart, selected ->
            EventsCalendarUiState(
                displayedMonth = monthStart,
                selectedDay = selected,
                eventsByDay = eventsByDay,
                today = today,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue =
                EventsCalendarUiState(
                    displayedMonth = today.startOfMonth(),
                    selectedDay = today,
                    today = today,
                ),
        )

    fun showPreviousMonth() {
        displayedMonthStart.value = displayedMonthStart.value.minus(1, DateTimeUnit.MONTH)
        // When jumping to a different month, drop the selection so the new month doesn't
        // open with a stale day from the previous month highlighted.
        selectedDay.value = null
    }

    fun showNextMonth() {
        displayedMonthStart.value = displayedMonthStart.value.plus(1, DateTimeUnit.MONTH)
        selectedDay.value = null
    }

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        private fun LocalDate.startOfMonth(): LocalDate = LocalDate(year, month, 1)
    }
}
