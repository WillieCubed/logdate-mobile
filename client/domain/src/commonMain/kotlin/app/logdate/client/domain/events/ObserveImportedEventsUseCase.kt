package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import app.logdate.shared.model.ExternalCalendarSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes events that came from the on-device calendar import worker, sorted by most
 * recently updated. Powers the calendar sync "recent imports" settings screen so the user
 * can scan what LogDate mirrored from their device calendars and tap through to a detail
 * view if they want to edit the title or attach captures.
 */
class ObserveImportedEventsUseCase(
    private val eventRepository: EventRepository,
) {
    operator fun invoke(): Flow<List<Event>> =
        eventRepository.observeAllEvents().map { events ->
            events
                .filter { it.externalCalendarSource == ExternalCalendarSource.DEVICE_CALENDAR }
                .sortedByDescending(Event::lastUpdated)
        }
}
