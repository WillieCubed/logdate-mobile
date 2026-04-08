package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Observes events that occur on any day in [monthStart] (the first day of a month) and
 * groups them by their start date. Used by the calendar surface tab to render a month
 * grid with the right counts in each cell without each cell having to walk the full
 * event list.
 *
 * The query expands a tiny pad (one day on each side) so events that straddle a day
 * boundary still surface in the cell where they began. The grouping uses the device's
 * current timezone, matching how every other LogDate surface bucks events to days.
 */
class ObserveEventsForMonthUseCase(
    private val eventRepository: EventRepository,
) {
    operator fun invoke(monthStart: LocalDate): Flow<Map<LocalDate, List<Event>>> {
        val timezone = TimeZone.currentSystemDefault()
        val nextMonthStart = monthStart.plus(1, DateTimeUnit.MONTH)
        val rangeStart = monthStart.atStartOfDayIn(timezone)
        val rangeEnd = nextMonthStart.atStartOfDayIn(timezone)
        return eventRepository
            .observeEventsForDateRange(rangeStart, rangeEnd)
            .map { events ->
                events
                    .groupBy { event ->
                        event.startTime.toLocalDateTime(timezone).date
                    }
            }
    }
}
