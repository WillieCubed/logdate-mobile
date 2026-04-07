package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Observes the journal note ids that are currently linked to a given event.
 *
 * Used by the event detail screen to render a "N linked items" line and to keep that count
 * fresh as the underlying junction table changes (notes are linked or unlinked).
 *
 * The returned flow emits the full list each time anything changes — callers that only need
 * the count should call `.size` on the emitted list. Returning the ids (rather than the count
 * directly) is intentional so future callers can resolve the actual notes without a new use
 * case.
 *
 * @param repository The event repository providing the underlying junction observation.
 */
class ObserveNotesForEventUseCase(
    private val repository: EventRepository,
) {
    /**
     * Returns a flow of the note ids linked to [eventId]. Emits an empty list when nothing is
     * linked. The flow stays open until the collector is cancelled.
     */
    operator fun invoke(eventId: Uuid): Flow<List<Uuid>> = repository.observeNotesForEvent(eventId)
}
