package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event

/**
 * Updates an event's mutable metadata (title, description, time, cover image, place).
 */
class UpdateEventUseCase(
    private val repository: EventRepository,
) {
    suspend operator fun invoke(event: Event): Result<Unit> = repository.updateEvent(event)
}
