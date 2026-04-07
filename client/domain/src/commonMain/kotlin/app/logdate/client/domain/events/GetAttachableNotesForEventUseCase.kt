package app.logdate.client.domain.events

import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One-shot lookup for the candidate notes a user might attach to an event from the attach
 * sheet.
 *
 * The candidate window is the supplied [aroundStart] ± [windowHours] hours, defaulting to ±24h.
 * Notes already linked to the event are filtered out so the picker only shows things the user
 * could add. Returned notes are sorted reverse-chronologically.
 *
 * One-shot rather than reactive: the attach sheet is opened on demand, lives for a few seconds,
 * and then closes. Continuously observing the time-range query while the sheet is shut would
 * burn database queries for nothing. The ViewModel calls this when the user opens the sheet,
 * passing the *current draft's* `startTime` so the window follows the user's edits.
 */
class GetAttachableNotesForEventUseCase(
    private val notesRepository: JournalNotesRepository,
    private val eventRepository: EventRepository,
) {
    suspend operator fun invoke(
        eventId: Uuid,
        aroundStart: Instant,
        windowHours: Long = 24,
    ): List<JournalNote> {
        val rangeStart = aroundStart - windowHours.hours
        val rangeEnd = aroundStart + windowHours.hours
        val candidates = notesRepository.observeNotesInRange(rangeStart, rangeEnd).first()
        val linkedIds = eventRepository.observeNotesForEvent(eventId).first().toSet()
        return candidates
            .filter { it.uid !in linkedIds }
            .sortedByDescending { it.creationTimestamp }
    }
}
