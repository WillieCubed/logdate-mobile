package app.logdate.client.domain.timeline

import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Request type for streaming timeline queries.
 */
sealed interface StreamingTimelineRequest {
    data class RecentTimeline(
        val sortOrder: TimelineSortOrder = TimelineSortOrder.REVERSE_CHRONOLOGICAL,
        val pageSize: Int = 50,
    ) : StreamingTimelineRequest

    data class TimelineInRange(
        val start: Instant,
        val end: Instant,
        val sortOrder: TimelineSortOrder = TimelineSortOrder.REVERSE_CHRONOLOGICAL,
    ) : StreamingTimelineRequest
}

/**
 * A streaming version of GetTimelineUseCase that loads notes incrementally
 * instead of waiting for all notes to be available.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetStreamingTimelineUseCase(
    private val notesRepository: JournalNotesRepository,
    private val getTimelineDayUseCase: GetTimelineDayUseCase,
    private val groupNotesByDayBoundsUseCase: GroupNotesByDayBoundsUseCase,
    private val eventRepository: EventRepository,
) {
    /**
     * Gets streaming timeline based on the request type
     */
    operator fun invoke(request: StreamingTimelineRequest = StreamingTimelineRequest.RecentTimeline()): Flow<Timeline> =
        when (request) {
            is StreamingTimelineRequest.RecentTimeline -> getRecentTimeline(request.sortOrder, request.pageSize)
            is StreamingTimelineRequest.TimelineInRange -> getTimelineInRange(request.start, request.end, request.sortOrder)
        }

    private fun getRecentTimeline(
        sortOrder: TimelineSortOrder,
        pageSize: Int,
    ): Flow<Timeline> =
        notesRepository
            .observeRecentNotes(pageSize)
            .combine(eventRepository.observeAllEvents()) { recentNotes, events -> recentNotes to events }
            .transformLatest { (recentNotes, events) ->
                val basicTimeline = createBasicTimeline(recentNotes, sortOrder, events)
                emit(basicTimeline)

                if (basicTimeline.days.isEmpty()) {
                    return@transformLatest
                }

                val recentDates = basicTimeline.days.map(TimelineDay::date).toSet()
                emitAll(
                    notesRepository.allNotesObserved.map { allNotes ->
                        createEnrichedTimeline(
                            recentDates = recentDates,
                            allNotes = allNotes,
                            fallbackNotes = recentNotes,
                            events = events,
                            sortOrder = sortOrder,
                        )
                    },
                )
            }.distinctUntilChanged()

    private fun getTimelineInRange(
        start: Instant,
        end: Instant,
        sortOrder: TimelineSortOrder,
    ): Flow<Timeline> =
        combine(
            notesRepository.observeNotesInRange(start, end),
            eventRepository.observeEventsForDateRange(start, end),
        ) { notesInRange, events ->
            val notesByDay = groupNotesByDayBoundsUseCase(notesInRange)

            val timelineDays =
                notesByDay.map { (date, entries) ->
                    getTimelineDayUseCase(date, entries, events.overlapping(entries))
                }

            val sortedDays =
                when (sortOrder) {
                    TimelineSortOrder.CHRONOLOGICAL -> timelineDays.sortedBy { it.date }
                    TimelineSortOrder.REVERSE_CHRONOLOGICAL -> timelineDays.sortedByDescending { it.date }
                }

            Timeline(sortedDays)
        }

    private fun createBasicTimeline(
        notes: List<JournalNote>,
        sortOrder: TimelineSortOrder,
        events: List<Event>,
    ): Timeline {
        val notesByDay =
            notes.groupBy { note ->
                note.creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date
            }

        val basicDays =
            notesByDay.map { (date, entries) ->
                val places = extractPlacesVisited(entries)
                TimelineDay(
                    start = entries.minOf { it.creationTimestamp },
                    end = entries.maxOf { it.creationTimestamp },
                    tldr = "", // Empty - UI will show loading placeholder via isLoadingSummary
                    date = date,
                    people = emptyList(),
                    events = events.overlapping(entries),
                    placesVisited = places,
                    moments = inferMomentsHeuristically(date, entries, places),
                    parts = extractDayParts(entries),
                    entries = entries.sortedByDescending { it.creationTimestamp },
                )
            }

        return Timeline(applySorting(basicDays, sortOrder))
    }

    private suspend fun createEnrichedTimeline(
        recentDates: Set<kotlinx.datetime.LocalDate>,
        allNotes: List<JournalNote>,
        fallbackNotes: List<JournalNote>,
        events: List<Event>,
        sortOrder: TimelineSortOrder,
    ): Timeline {
        val recentNotesByDay =
            fallbackNotes.groupBy { note ->
                note.creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
        val expandedDates =
            recentDates +
                recentDates.mapNotNull { date ->
                    date.minus(1, DateTimeUnit.DAY).takeIf { it !in recentDates }
                }
        val relevantNotes =
            allNotes.filter { note ->
                note.creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date in expandedDates
            }
        val allNotesByDay = groupNotesByDayBoundsUseCase(relevantNotes)

        val days =
            recentDates.mapNotNull { date ->
                val entries = allNotesByDay[date] ?: recentNotesByDay[date]
                entries?.let { dayEntries ->
                    if (allNotesByDay.containsKey(date)) {
                        getTimelineDayUseCase(date, dayEntries, events.overlapping(dayEntries))
                    } else {
                        createBasicTimeline(dayEntries, sortOrder, events).days.firstOrNull()
                    }
                }
            }

        return Timeline(applySorting(days, sortOrder))
    }

    private fun applySorting(
        days: List<TimelineDay>,
        sortOrder: TimelineSortOrder,
    ): List<TimelineDay> =
        when (sortOrder) {
            TimelineSortOrder.CHRONOLOGICAL -> days.sortedBy { it.date }
            TimelineSortOrder.REVERSE_CHRONOLOGICAL -> days.sortedByDescending { it.date }
        }
}

private fun List<Event>.overlapping(entries: List<JournalNote>): List<Event> {
    if (entries.isEmpty()) return emptyList()
    val dayStart = entries.minOf { it.creationTimestamp }
    val dayEnd = entries.maxOf { it.creationTimestamp }
    return filter { event ->
        event.startTime < dayEnd && (event.endTime ?: event.startTime) >= dayStart
    }
}
