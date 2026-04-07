package app.logdate.client.domain.recommendation

import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Empty [EventRepository] used by the recommendation use-case tests that don't care about
 * events. Returns no events from every read so the upstream behavior matches a brand-new
 * install with no auto-generated events yet.
 */
internal object StubEventRepository : EventRepository {
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

/**
 * In-memory [EventRepository] that lets a test seed events plus the notes attached to each.
 * Used by the recommendation tests that need to verify event-driven branches without spinning
 * up a real Room database.
 */
internal class SeedableEventRepository(
    events: List<Event> = emptyList(),
    notesByEvent: Map<Uuid, List<Uuid>> = emptyMap(),
) : EventRepository {
    private val eventsFlow = MutableStateFlow(events)
    private val notesByEventFlow = MutableStateFlow(notesByEvent)

    override fun observeAllEvents(): Flow<List<Event>> = eventsFlow

    override fun observeEvent(eventId: Uuid): Flow<Event?> = flowOf(eventsFlow.value.firstOrNull { it.id == eventId })

    override fun observeEventsForDateRange(
        start: Instant,
        end: Instant,
    ): Flow<List<Event>> = flowOf(eventsFlow.value.filter { it.startTime in start..end })

    override suspend fun getEventById(eventId: Uuid): Event? = eventsFlow.value.firstOrNull { it.id == eventId }

    override suspend fun updateEvent(event: Event): Result<Unit> = Result.success(Unit)

    override suspend fun deleteEvent(eventId: Uuid): Result<Unit> = Result.success(Unit)

    override fun observeEventsForNote(noteId: Uuid): Flow<List<Event>> = flowOf(emptyList())

    override fun observeNotesForEvent(eventId: Uuid): Flow<List<Uuid>> = flowOf(notesByEventFlow.value[eventId].orEmpty())

    override suspend fun linkNoteToEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun unlinkNoteFromEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = Result.success(Unit)
}
