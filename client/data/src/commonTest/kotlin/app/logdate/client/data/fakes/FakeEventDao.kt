package app.logdate.client.data.fakes

import app.logdate.client.database.dao.EventDao
import app.logdate.client.database.entities.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * In-memory test double for [EventDao].
 *
 * Backed by a mutable map keyed by event id and a [MutableStateFlow] that broadcasts the
 * non-deleted entities to observers. Mirrors three behaviors of the real Room DAO that the
 * production code relies on:
 *
 * 1. **Soft-delete visibility:** every observable query filters out entities whose
 *    `deletedAt` field is non-null, matching the SQL `WHERE deleted_at IS NULL` clauses on
 *    the real DAO. [softDelete] sets the field rather than removing the entity, so a future
 *    "show deleted" feature could read the same map.
 * 2. **Reactive emissions:** every mutation pushes a fresh snapshot through [eventsFlow] so
 *    `observe…` flows recompose immediately.
 * 3. **Date-range overlap:** [observeForDateRange] uses the same predicate as the SQL —
 *    `startTime < end && (endTime ?: startTime) >= start` — so tests exercise the overlap
 *    semantics correctly even though no real database is involved.
 */
class FakeEventDao : EventDao {
    private val events = mutableMapOf<Uuid, EventEntity>()
    private val eventsFlow = MutableStateFlow<List<EventEntity>>(emptyList())

    override suspend fun insert(event: EventEntity) {
        events[event.id] = event
        publish()
    }

    override suspend fun update(event: EventEntity) {
        events[event.id] = event
        publish()
    }

    override suspend fun getById(id: Uuid): EventEntity? = events[id]?.takeIf { it.deletedAt == null }

    override suspend fun getByIds(ids: List<Uuid>): List<EventEntity> =
        ids.mapNotNull { events[it]?.takeIf { entity -> entity.deletedAt == null } }

    override fun observeById(id: Uuid): Flow<EventEntity?> = eventsFlow.map { list -> list.find { it.id == id } }

    override fun observeAll(): Flow<List<EventEntity>> = eventsFlow.map { list -> list.sortedByDescending { it.startTime } }

    override suspend fun getAll(): List<EventEntity> = events.values.filter { it.deletedAt == null }.sortedByDescending { it.startTime }

    override fun observeForDateRange(
        start: Instant,
        end: Instant,
    ): Flow<List<EventEntity>> =
        eventsFlow.map { list ->
            list
                .filter { event -> event.startTime < end && (event.endTime ?: event.startTime) >= start }
                .sortedBy { it.startTime }
        }

    override suspend fun getByExternalCalendarId(externalId: String): EventEntity? =
        events.values.firstOrNull { it.externalCalendarId == externalId && it.deletedAt == null }

    override suspend fun softDelete(
        id: Uuid,
        deletedAt: Long,
    ) {
        val existing = events[id] ?: return
        events[id] = existing.copy(deletedAt = Instant.fromEpochMilliseconds(deletedAt))
        publish()
    }

    override suspend fun hardDelete(id: Uuid) {
        events.remove(id)
        publish()
    }

    private fun publish() {
        eventsFlow.value = events.values.filter { it.deletedAt == null }
    }
}
