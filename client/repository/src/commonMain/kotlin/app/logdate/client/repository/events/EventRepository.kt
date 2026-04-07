package app.logdate.client.repository.events

import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Repository for the [Event] primitive.
 *
 * Events are time-bound things that media and notes attach to. They are not user-created;
 * the public API intentionally has no `createEvent` method. Events are introduced by an
 * internal pipeline (auto-generation from patterns or grounding from external calendars).
 * Users can read, edit, and delete them, and link/unlink notes to them.
 */
interface EventRepository {
    /**
     * Observes all non-deleted events, sorted by start time descending.
     */
    fun observeAllEvents(): Flow<List<Event>>

    /**
     * Observes a single event by id. Emits `null` if the event does not exist or is deleted.
     */
    fun observeEvent(eventId: Uuid): Flow<Event?>

    /**
     * Observes events that overlap the given inclusive-exclusive time range `[start, end)`.
     * Used by the timeline to surface events alongside the day's notes and moments.
     */
    fun observeEventsForDateRange(
        start: Instant,
        end: Instant,
    ): Flow<List<Event>>

    /**
     * Fetches a single event by id, or `null` if it does not exist.
     */
    suspend fun getEventById(eventId: Uuid): Event?

    /**
     * Updates an event's mutable metadata. The event must already exist.
     */
    suspend fun updateEvent(event: Event): Result<Unit>

    /**
     * Soft-deletes an event. Linked notes are not deleted, but their links are removed.
     */
    suspend fun deleteEvent(eventId: Uuid): Result<Unit>

    /**
     * Observes the events that a note is linked to.
     */
    fun observeEventsForNote(noteId: Uuid): Flow<List<Event>>

    /**
     * Observes the note ids linked to an event.
     */
    fun observeNotesForEvent(eventId: Uuid): Flow<List<Uuid>>

    /**
     * Links a note to an event.
     */
    suspend fun linkNoteToEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit>

    /**
     * Removes the link between a note and an event.
     */
    suspend fun unlinkNoteFromEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit>
}
