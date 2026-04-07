package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Observes the events that a given note is linked to.
 */
class ObserveEventsForNoteUseCase(
    private val repository: EventRepository,
) {
    operator fun invoke(noteId: Uuid): Flow<List<Event>> = repository.observeEventsForNote(noteId)
}
