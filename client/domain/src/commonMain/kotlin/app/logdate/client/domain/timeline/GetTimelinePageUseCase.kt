package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class TimelinePageRequest(
    val beforeExclusive: Instant? = null,
    val pageSize: Int = 50,
    val sortOrder: TimelineSortOrder = TimelineSortOrder.REVERSE_CHRONOLOGICAL,
)

data class TimelinePage(
    val days: List<TimelineDay> = emptyList(),
    val oldestLoadedTimestamp: Instant? = null,
    val hasMoreOlderContent: Boolean = false,
)

class GetTimelinePageUseCase(
    private val notesRepository: JournalNotesRepository,
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
        val timelineDays =
            pageDates.mapNotNull { pageDate ->
                notesRepository.getNotesForDay(pageDate).takeIf { it.isNotEmpty() }?.let { entries ->
                    createBasicTimeline(entries, request.sortOrder).days.firstOrNull()
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

private fun createBasicTimeline(
    notes: List<JournalNote>,
    sortOrder: TimelineSortOrder,
): Timeline {
    val notesByDay = notes.groupBy(JournalNote::timelineDate)

    val basicDays =
        notesByDay.map { (date, entries) ->
            TimelineDay(
                start = entries.minOf { it.creationTimestamp },
                end = entries.maxOf { it.creationTimestamp },
                tldr = "",
                date = date,
                people = emptyList(),
                events = emptyList(),
                placesVisited = extractPlacesVisited(entries),
                parts = extractDayParts(entries),
                entries = entries.sortedByDescending { it.creationTimestamp },
            )
        }

    return Timeline(applySorting(basicDays, sortOrder))
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
