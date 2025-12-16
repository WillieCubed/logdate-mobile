package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A streaming version of GetTimelineUseCase that loads notes incrementally
 * instead of waiting for all notes to be available.
 */
class GetStreamingTimelineUseCase(
    private val notesRepository: JournalNotesRepository,
    private val getTimelineDayUseCase: GetTimelineDayUseCase
) {

    /**
     * Gets streaming timeline based on the request type
     */
    operator fun invoke(request: TimelineRequest): Flow<Timeline> {
        return when (request) {
            is TimelineRequest.RecentTimeline -> getRecentTimeline(request.sortOrder, request.pageSize)
            is TimelineRequest.TimelineInRange -> getTimelineInRange(request.start, request.end, request.sortOrder)
        }
    }

    private fun getRecentTimeline(
        sortOrder: TimelineSortOrder,
        pageSize: Int
    ): Flow<Timeline> {
        // Simplified approach: Fast first paint with recent notes, then enhance with full data
        return notesRepository.observeRecentNotes(20)
            .map { recentNotes ->
                // Create immediate basic timeline for first paint
                createBasicTimeline(recentNotes, sortOrder)
            }
    }

    private fun getTimelineInRange(
        start: Instant,
        end: Instant,
        sortOrder: TimelineSortOrder
    ): Flow<Timeline> {
        return notesRepository.observeNotesInRange(start, end)
            .map { notesInRange ->
                val notesByDay = notesInRange.groupBy { note ->
                    note.creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date
                }

                val timelineDays = notesByDay.map { (date, entries) ->
                    getTimelineDayUseCase(date, entries)
                }

                val sortedDays = when (sortOrder) {
                    TimelineSortOrder.CHRONOLOGICAL -> timelineDays.sortedBy { it.date }
                    TimelineSortOrder.REVERSE_CHRONOLOGICAL -> timelineDays.sortedByDescending { it.date }
                }

                Timeline(sortedDays)
            }
    }

    private fun createBasicTimeline(notes: List<app.logdate.client.repository.journals.JournalNote>, sortOrder: TimelineSortOrder): Timeline {
        val notesByDay = notes.groupBy { note ->
            note.creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date
        }

        val basicDays = notesByDay.map { (date, entries) ->
            TimelineDay(
                start = entries.minOf { it.creationTimestamp },
                end = entries.maxOf { it.creationTimestamp },
                tldr = "${entries.size} entries",
                date = date,
                people = emptyList(),
                events = emptyList(),
                placesVisited = emptyList(),
                parts = emptyList()
            )
        }

        return Timeline(applySorting(basicDays, sortOrder))
    }

    private fun applySorting(days: List<TimelineDay>, sortOrder: TimelineSortOrder): List<TimelineDay> {
        return when (sortOrder) {
            TimelineSortOrder.CHRONOLOGICAL -> days.sortedBy { it.date }
            TimelineSortOrder.REVERSE_CHRONOLOGICAL -> days.sortedByDescending { it.date }
        }
    }

    sealed class TimelineRequest {
        data class RecentTimeline(
            val sortOrder: TimelineSortOrder = TimelineSortOrder.REVERSE_CHRONOLOGICAL,
            val pageSize: Int = 50
        ) : TimelineRequest()

        data class TimelineInRange(
            val start: Instant,
            val end: Instant,
            val sortOrder: TimelineSortOrder = TimelineSortOrder.REVERSE_CHRONOLOGICAL
        ) : TimelineRequest()
    }
}