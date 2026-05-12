package app.logdate.client.domain.timeline

import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Request for an older or recent timeline page.
 */
data class TimelinePageRequest(
    /**
     * Cursor timestamp. When present, the page starts before this note timestamp.
     */
    val beforeExclusive: Instant? = null,
    /**
     * Number of notes used to choose candidate days. Returned days are always complete, so the
     * response may include more than this many entries when a dense day crosses the cursor window.
     */
    val pageSize: Int = 50,
    /**
     * Sort order for the returned days.
     */
    val sortOrder: TimelineSortOrder = TimelineSortOrder.REVERSE_CHRONOLOGICAL,
)

/**
 * A timeline page containing complete day cards.
 */
data class TimelinePage(
    /**
     * Complete timeline days included in this page.
     */
    val days: List<TimelineDay> = emptyList(),
    /**
     * Oldest note timestamp represented by [days]. Use as the next [TimelinePageRequest.beforeExclusive].
     */
    val oldestLoadedTimestamp: Instant? = null,
    /**
     * Whether older notes exist before [oldestLoadedTimestamp].
     */
    val hasMoreOlderContent: Boolean = false,
)

class GetTimelinePageUseCase(
    private val notesRepository: JournalNotesRepository,
    private val groupNotesByDayBoundsUseCase: GroupNotesByDayBoundsUseCase,
    private val eventRepository: EventRepository,
    private val timelineDayBuilder: TimelineDayBuilder,
) {
    suspend operator fun invoke(request: TimelinePageRequest = TimelinePageRequest()): TimelinePage {
        val candidateNotes =
            request.beforeExclusive?.let { beforeExclusive ->
                notesRepository.getNotesBefore(
                    beforeExclusive = beforeExclusive,
                    limit = request.pageSize,
                )
            } ?: notesRepository.observeRecentNotes(request.pageSize).first()

        if (candidateNotes.isEmpty()) {
            return TimelinePage()
        }

        val pageDates = candidateNotes.map(JournalNote::timelineDate).toSet()
        val allNotesForDates = pageDates.flatMap { notesRepository.getNotesForDay(it) }
        val allEvents = eventRepository.observeAllEvents().first()
        val notesByDay = groupNotesByDayBoundsUseCase(allNotesForDates)
        val timelineDays =
            notesByDay.mapNotNull { (_, entries) ->
                if (entries.isEmpty()) {
                    null
                } else {
                    val date = entries.first().timelineDate()
                    timelineDayBuilder(date, entries, allEvents.overlapping(entries))
                }
            }

        val sortedDays = applySorting(timelineDays, request.sortOrder)
        val oldestLoadedTimestamp =
            sortedDays
                .flatMap(TimelineDay::entries)
                .minOfOrNull { entry -> entry.creationTimestamp }

        val hasMoreOlderContent =
            oldestLoadedTimestamp?.let { cursor ->
                notesRepository.hasNotesBefore(cursor)
            } ?: false

        return TimelinePage(
            days = sortedDays,
            oldestLoadedTimestamp = oldestLoadedTimestamp,
            hasMoreOlderContent = hasMoreOlderContent,
        )
    }
}

private fun applySorting(
    days: List<TimelineDay>,
    sortOrder: TimelineSortOrder,
): List<TimelineDay> =
    when (sortOrder) {
        TimelineSortOrder.CHRONOLOGICAL -> days.sortedBy(TimelineDay::date)
        TimelineSortOrder.REVERSE_CHRONOLOGICAL -> days.sortedByDescending(TimelineDay::date)
    }

private fun JournalNote.timelineDate() = creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun List<Event>.overlapping(entries: List<JournalNote>): List<Event> {
    if (entries.isEmpty()) return emptyList()
    val dayStart = entries.minOf { it.creationTimestamp }
    val dayEnd = entries.maxOf { it.creationTimestamp }
    return filter { event ->
        event.startTime < dayEnd && (event.endTime ?: event.startTime) >= dayStart
    }
}
