package app.logdate.feature.timeline.domain

import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.model.Person
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.profiles.toUiState
import jakarta.inject.Inject
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
class GetTimelineUseCase @Inject constructor(
    private val notesRepository: JournalNotesRepository,
    private val summarizeJournalEntriesUseCase: SummarizeJournalEntriesUseCase,
    private val getMediaUrisUseCase: GetMediaUrisUseCase,
    private val extractPeopleUseCase: ExtractPeopleUseCase,
) {

    operator fun invoke(): Flow<Timeline> {
        return notesRepository.allNotesObserved
            .transform {
                val notesByDay =
                    it.groupBy { note -> note.creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date }
                        .toSortedMap(compareByDescending { it })
                val people = extractPeopleUseCase(it.joinToString("\n"))
                val summarizedEntries = notesByDay.map { (date, entries) ->
                    val summary = when (val result = summarizeJournalEntriesUseCase(entries)) {
                        SummarizeJournalEntriesResult.NetworkUnavailable -> "Summary currently not available."
                        is SummarizeJournalEntriesResult.Success -> {
                            result.summary
                        }

                        SummarizeJournalEntriesResult.SummaryUnavailable -> "No summary available."
                    }
                    val mediaUris = getMediaUrisUseCase(date)
                    TimelineDay(
                        tldr = summary,
                        date = date,
                        start = entries.minOf { it.creationTimestamp },
                        end = entries.maxOf { it.creationTimestamp },
                        people = people.map(Person::toUiState),
                    )
                }
                emit(Timeline(summarizedEntries))
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
    val people: List<PersonUiState> = emptyList(),
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
    val label: String,
    val description: String? = null,
    val featuredGraphicUri: String?,
)