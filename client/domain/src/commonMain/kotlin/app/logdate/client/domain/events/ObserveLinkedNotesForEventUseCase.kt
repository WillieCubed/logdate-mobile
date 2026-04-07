package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlin.uuid.Uuid

/**
 * Observes the journal notes that are currently linked to a given event.
 *
 * Builds on [EventRepository.observeNotesForEvent] (which only emits ids) by resolving each
 * id into a full [JournalNote] via [JournalNotesRepository]. The resolved list is what the
 * event detail editor renders.
 *
 * The cardinality of notes-per-event is small (typically 0–10), so a per-id lookup is fine —
 * the underlying batch query in `OfflineFirstEventRepository` already collapsed the previous
 * N+1 on the event side. If we ever need to render hundreds of linked notes per event, this
 * is the place to add a single batched note fetch.
 */
class ObserveLinkedNotesForEventUseCase(
    private val eventRepository: EventRepository,
    private val notesRepository: JournalNotesRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(eventId: Uuid): Flow<List<JournalNote>> =
        eventRepository.observeNotesForEvent(eventId).flatMapLatest { noteIds ->
            flow {
                val notes = noteIds.mapNotNull { id -> notesRepository.getNoteById(id) }
                emit(notes)
            }
        }
}
