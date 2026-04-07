package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Observes events that are starting soon, ordered by start time ascending.
 *
 * Drives the recommendation surfaces (ambient prompts, home screen card). An event qualifies
 * when its start time falls within `[now, now + window]` (default: next six hours).
 *
 * The use case intentionally does **not** filter by "has captures yet" — that check belongs
 * one layer up in the recommendation use case, where it can be applied lazily to the (small)
 * candidate set without forcing an N+1 over the full event stream.
 *
 * @param eventRepository Source of all events. The use case observes the full stream and
 *   filters in memory; cardinality is small in practice (events are auto-generated, not
 *   bulk-imported).
 * @param now Clock provider for tests; defaults to the system clock.
 */
class ObserveUpcomingEventsUseCase(
    private val eventRepository: EventRepository,
    private val now: () -> Instant = { Clock.System.now() },
) {
    operator fun invoke(window: Duration = DEFAULT_WINDOW): Flow<List<Event>> =
        eventRepository.observeAllEvents().map { allEvents ->
            val currentTime = now()
            val deadline = currentTime + window
            allEvents
                .filter { event -> event.startTime in currentTime..deadline }
                .sortedBy { it.startTime }
        }

    private companion object {
        val DEFAULT_WINDOW = 6.hours
    }
}
