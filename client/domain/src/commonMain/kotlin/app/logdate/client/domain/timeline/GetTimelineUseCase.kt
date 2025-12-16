package app.logdate.client.domain.timeline

import app.logdate.client.domain.entities.ExtractPeopleUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.shared.model.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A use case to get the timeline of timeline events.
 *
 * This produces a timeline that aggregates written notes, media, and other data into a single
 * stream of events.
 */
class GetTimelineUseCase(
    private val notesRepository: JournalNotesRepository,
    private val getTimelineDayUseCase: GetTimelineDayUseCase
) {

    operator fun invoke(sortOrder: TimelineSortOrder = TimelineSortOrder.REVERSE_CHRONOLOGICAL): Flow<Timeline> {
        return notesRepository.allNotesObserved
            .transform { allNotes ->
                // Group notes by day using string components for more reliable grouping
                val improvedNotesByDay = mutableMapOf<LocalDate, MutableList<JournalNote>>()
                
                allNotes.forEach { note ->
                    val noteDateTime = note.creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                    val noteDate = noteDateTime.date
                    
                    if (!improvedNotesByDay.containsKey(noteDate)) {
                        improvedNotesByDay[noteDate] = mutableListOf()
                    }
                    
                    improvedNotesByDay[noteDate]?.add(note)
                }
                // Process each day's entries
                val timelineDays = improvedNotesByDay.map { (date, entries) ->
                    getTimelineDayUseCase(date, entries)
                }

                // Apply sorting based on the requested order
                val sortedDays = when (sortOrder) {
                    TimelineSortOrder.CHRONOLOGICAL -> timelineDays.sortedBy { it.date }
                    TimelineSortOrder.REVERSE_CHRONOLOGICAL -> timelineDays.sortedByDescending { it.date }
                }

                emit(Timeline(sortedDays))
            }
    }
}

data class Timeline(
    val days: List<TimelineDay>,
)

data class TimelineDay(
    /**
     * The conceptual start of this day.
     *
     * This does not correspond to the actual start of the day, but rather the start of the first
     * activity that would be reasonably considered the start of the day. For example, if the first
     * activity in the day is at 8 a.m., the start time would be the UTC timestamp corresponding to
     * 8 a.m.
     */
    val start: Instant,
    /**
     * The conceptual end to this day.
     *
     * This boundary is used to group events into days. This does not correspond to the literal last
     * instant of the calendar day for this day, but rather the end of the last activity that can be
     * associated with this day. For example, for a day beginning on October 28, there may be an
     * activity that ends at 2:30 a.m. on October 29. In this case, the `end` time would be the UTC
     * timestamp corresponding to 2:30 a.m. on October 29.
     */
    val end: Instant,
    val tldr: String,
    val date: LocalDate,
    val people: List<Person> = emptyList(),
    val events: List<String> = emptyList(), // TODO: Actually include events
    val placesVisited: List<String> = emptyList(), // TODO: Actually include places visited
    val parts: List<DayPart> = emptyList(),
//    val mediaUris: List<MediaObjectUiState> = emptyList(), // TODO: Actually include media
)

data class DayPart(
    /**
     * A label that provides context to the part.
     *
     * Example: Party at Dan's Place
     */
    val label: String = "",
    val description: String? = null,
    val featuredGraphicUri: String?,
)

enum class TimelineSortOrder {
    CHRONOLOGICAL,
    REVERSE_CHRONOLOGICAL
}

/**
 * A use case to transform journal entries for a single day into a TimelineDay.
 */
class GetTimelineDayUseCase(
    private val summarizeJournalEntriesUseCase: SummarizeJournalEntriesUseCase,
    private val getMediaUrisUseCase: GetMediaUrisUseCase,
    private val extractPeopleUseCase: ExtractPeopleUseCase,
) {

    /**
     * Creates a TimelineDay from journal entries for a specific date.
     *
     * @param date The date for which to create the TimelineDay
     * @param entries The journal entries for the date
     * @return A TimelineDay object representing the aggregated data for the day
     */
    suspend operator fun invoke(date: LocalDate, entries: List<JournalNote>): TimelineDay {
        val summary = when (val result = summarizeJournalEntriesUseCase(entries)) {
            SummarizeJournalEntriesResult.NetworkUnavailable -> "Summary currently not available."
            is SummarizeJournalEntriesResult.Success -> result.summary
            SummarizeJournalEntriesResult.SummaryUnavailable -> "No summary available."
        }

        val mediaUris = getMediaUrisUseCase(date)
        val people = extractPeopleUseCase(
            documentId = "people_summary_" + date.toEpochDays().toString(),
            text = entries.filterIsInstance<JournalNote.Text>()
                .joinToString("\n") { note -> note.content }
        )

        return TimelineDay(
            tldr = summary,
            date = date,
            start = entries.minOf { entry -> entry.creationTimestamp },
            end = entries.maxOf { it.creationTimestamp },
            people = people,
        )
    }
}