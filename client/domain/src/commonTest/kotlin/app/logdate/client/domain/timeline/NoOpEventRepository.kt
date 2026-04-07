package app.logdate.client.domain.timeline

import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * No-op [EventRepository] for timeline tests that don't care about events.
 */
internal object NoOpEventRepository : EventRepository {
    override fun observeAllEvents(): Flow<List<Event>> = flowOf(emptyList())

    override fun observeEvent(eventId: Uuid): Flow<Event?> = flowOf(null)

    override fun observeEventsForDateRange(
        start: Instant,
        end: Instant,
    ): Flow<List<Event>> = flowOf(emptyList())

    override suspend fun getEventById(eventId: Uuid): Event? = null

    override suspend fun updateEvent(event: Event): Result<Unit> = Result.success(Unit)

    override suspend fun deleteEvent(eventId: Uuid): Result<Unit> = Result.success(Unit)

    override fun observeEventsForNote(noteId: Uuid): Flow<List<Event>> = flowOf(emptyList())

    override fun observeNotesForEvent(eventId: Uuid): Flow<List<Uuid>> = flowOf(emptyList())

    override suspend fun linkNoteToEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun unlinkNoteFromEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = Result.success(Unit)
}
