package app.logdate.client.data.fakes

import app.logdate.client.database.dao.EventNoteLinkDao
import app.logdate.client.database.entities.EventNoteLinkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * In-memory test double for [EventNoteLinkDao].
 *
 * Stores links as a `Set<Pair<eventId, noteId>>` and broadcasts the full set through a
 * [MutableStateFlow] so that observers (filtered by event or by note) see updates immediately
 * after [insert] or [delete].
 *
 * The fake does not model the real DAO's `deletedAt` column on links — production code never
 * soft-deletes individual links today, so the fake simply removes them from the set.
 */
class FakeEventNoteLinkDao : EventNoteLinkDao {
    private val links = mutableSetOf<Pair<Uuid, Uuid>>()
    private val linksFlow = MutableStateFlow<Set<Pair<Uuid, Uuid>>>(emptySet())

    override suspend fun insert(link: EventNoteLinkEntity) {
        links.add(link.eventId to link.noteId)
        publish()
    }

    override suspend fun delete(
        eventId: Uuid,
        noteId: Uuid,
    ) {
        links.remove(eventId to noteId)
        publish()
    }

    override fun getNotesForEvent(eventId: Uuid): Flow<List<Uuid>> =
        linksFlow.map { current ->
            current.filter { it.first == eventId }.map { it.second }
        }

    override fun getEventsForNote(noteId: Uuid): Flow<List<Uuid>> =
        linksFlow.map { current ->
            current.filter { it.second == noteId }.map { it.first }
        }

    private fun publish() {
        linksFlow.value = links.toSet()
    }
}
