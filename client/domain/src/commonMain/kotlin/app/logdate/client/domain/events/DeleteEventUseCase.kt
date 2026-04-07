package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import kotlin.uuid.Uuid

/**
 * Soft-deletes an event. Linked notes are unaffected; only the event entry is hidden.
 */
class DeleteEventUseCase(
    private val repository: EventRepository,
) {
    suspend operator fun invoke(eventId: Uuid): Result<Unit> = repository.deleteEvent(eventId)
}
