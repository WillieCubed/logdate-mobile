package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import kotlin.uuid.Uuid

/**
 * Attaches a journal note to an event.
 *
 * Used by the event detail editor when the user picks a note from the attach sheet. The link
 * is stored in the event-note junction table; the note itself is unchanged. Linking the same
 * note twice is a no-op (the underlying DAO uses `ON CONFLICT REPLACE`).
 */
class LinkNoteToEventUseCase(
    private val repository: EventRepository,
) {
    /**
     * Creates a junction row pointing the given note at the given event.
     *
     * @return [Result.success] when the link is persisted (whether or not it already existed),
     *   or [Result.failure] when the underlying write fails.
     */
    suspend operator fun invoke(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = repository.linkNoteToEvent(eventId, noteId)
}
