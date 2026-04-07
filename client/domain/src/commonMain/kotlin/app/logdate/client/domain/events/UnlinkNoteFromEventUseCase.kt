package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import kotlin.uuid.Uuid

/**
 * Removes the link between a journal note and an event.
 *
 * Used by the event detail editor when the user taps the unlink action on a linked note. The
 * note itself is left intact — only the junction row is removed. Unlinking a note that is not
 * currently linked is a no-op.
 */
class UnlinkNoteFromEventUseCase(
    private val repository: EventRepository,
) {
    /**
     * Removes the junction row pointing the given note at the given event.
     *
     * @return [Result.success] when the unlink completes (or there was nothing to remove),
     *   or [Result.failure] when the underlying write fails.
     */
    suspend operator fun invoke(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = repository.unlinkNoteFromEvent(eventId, noteId)
}
