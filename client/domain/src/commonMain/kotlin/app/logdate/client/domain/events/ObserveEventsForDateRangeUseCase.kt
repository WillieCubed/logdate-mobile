package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Observes events that overlap a given time range.
 *
 * Used by the timeline to surface event cards on the days they happen.
 */
class ObserveEventsForDateRangeUseCase(
    private val repository: EventRepository,
) {
    operator fun invoke(
        start: Instant,
        end: Instant,
    ): Flow<List<Event>> = repository.observeEventsForDateRange(start, end)
}
