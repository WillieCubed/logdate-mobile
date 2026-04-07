package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Observes an event by its id. Emits `null` if the event has been deleted or does not exist.
 */
class GetEventByIdUseCase(
    private val repository: EventRepository,
) {
    operator fun invoke(eventId: Uuid): Flow<Event?> = repository.observeEvent(eventId)
}
