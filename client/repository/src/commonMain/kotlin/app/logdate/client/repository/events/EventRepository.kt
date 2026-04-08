package app.logdate.client.repository.events

import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Repository for the [Event] primitive.
 *
 * Events are time-bound things that media and notes attach to. They are not directly created
 * by user actions in the UI — there is no "new event" button. Events are introduced by
 * background pipelines: the on-device inference job that clusters location stops and media
 * bursts into "things that happened", and the device calendar import that grounds events
 * from the OS calendar provider. The [createEvent] method is the entry point for those
 * pipelines.
 *
 * Users can read events, edit their metadata, attach or detach captures, and delete them.
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
     * Looks up an existing event by its [Event.externalCalendarId]. Used by the device
     * calendar import worker to dedupe — when a re-sync sees an event whose external id
     * already maps to a LogDate event, the worker updates the existing row in place
     * instead of creating a duplicate. Returns `null` if no event has that external id
     * (or if the matching event has been soft-deleted).
     */
    suspend fun findByExternalCalendarId(externalId: String): Event?

    /**
     * Persists a new event.
     *
     * **Not for direct use from UI code.** Events are not user-created — call sites should be
     * the on-device inference worker, the device calendar import worker, or other background
     * pipelines that materialize events from outside signals. The editor screen calls
     * [updateEvent] / [deleteEvent], never this.
     *
     * Generates no id of its own — pass an [Event] with the id you want it stored under.
     * Returns failure when the underlying database write fails (which should be vanishingly
     * rare in practice but is propagated rather than swallowed for observability).
     */
    suspend fun createEvent(event: Event): Result<Unit>

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
