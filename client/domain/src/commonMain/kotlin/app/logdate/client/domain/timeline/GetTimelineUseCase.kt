package app.logdate.client.domain.timeline

import app.logdate.client.domain.entities.ExtractPeopleUseCase
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.MAX_AI_INPUT_CHARACTERS
import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.shared.model.Event
import app.logdate.shared.model.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlin.time.Instant

/**
 * A use case to get the timeline of timeline events.
 *
 * This produces a timeline that aggregates written notes, media, and other data into a single
 * stream of events.
 */
class GetTimelineUseCase(
    private val notesRepository: JournalNotesRepository,
    private val getTimelineDayUseCase: GetTimelineDayUseCase,
    private val groupNotesByDayBoundsUseCase: GroupNotesByDayBoundsUseCase,
    private val eventRepository: EventRepository,
) {
    operator fun invoke(sortOrder: TimelineSortOrder = TimelineSortOrder.REVERSE_CHRONOLOGICAL): Flow<Timeline> =
        combine(
            notesRepository.allNotesObserved,
            eventRepository.observeAllEvents(),
        ) { allNotes, allEvents ->
            val notesByDay = groupNotesByDayBoundsUseCase(allNotes)

            val timelineDays =
                notesByDay.map { (date, entries) ->
                    val dayStart = entries.minOf { it.creationTimestamp }
                    val dayEnd = entries.maxOf { it.creationTimestamp }
                    val dayEvents =
                        allEvents.filter { event ->
                            event.startTime < dayEnd && (event.endTime ?: event.startTime) >= dayStart
                        }
                    getTimelineDayUseCase(date, entries, dayEvents)
                }

            val sortedDays =
                when (sortOrder) {
                    TimelineSortOrder.CHRONOLOGICAL -> timelineDays.sortedBy { it.date }
                    TimelineSortOrder.REVERSE_CHRONOLOGICAL -> timelineDays.sortedByDescending { it.date }
                }

            Timeline(sortedDays)
        }
}

data class Timeline(
    /**
     * Timeline days ready for presentation in the requested sort order.
     */
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
    /**
     * Short summary of the day written by the summarizer, or empty when there is none yet or
     * none can be made. [summaryUnavailableReason] says which.
     */
    val tldr: String,
    /**
     * Calendar date represented by this timeline day.
     */
    val date: LocalDate,
    /**
     * People inferred from the day's journal entries.
     */
    val people: List<Person> = emptyList(),
    /**
     * Calendar or app events that overlap this day's activity bounds.
     */
    val events: List<Event> = emptyList(),
    /**
     * Places visited during this day, de-duplicated by place or coordinate.
     */
    val placesVisited: List<TimelinePlaceVisit> = emptyList(),
    /**
     * Higher-level moments inferred from entries and places.
     */
    val moments: List<Moment> = emptyList(),
    /**
     * Time-of-day sections used by timeline presentation.
     */
    val parts: List<DayPart> = emptyList(),
    /**
     * Raw journal notes that contributed to this day.
     */
    val entries: List<JournalNote> = emptyList(),
    val marks: List<app.logdate.client.repository.moments.MarkedMoment> = emptyList(),
    /**
     * Why the day has no summary, or null when it has one or one is still being made.
     */
    val summaryUnavailableReason: TimelineSummaryUnavailableReason? = null,
) {
    /** True while the summary has not been made yet and nothing says it cannot be. */
    val isSummaryPending: Boolean
        get() = tldr.isEmpty() && summaryUnavailableReason == null
}

/**
 * Why a [TimelineDay] has no summary. The screen chooses the words for each reason.
 */
enum class TimelineSummaryUnavailableReason {
    /** The summarizer needs a network connection and none is available. */
    NO_NETWORK,

    /** The summarizer cannot make a summary for this day. */
    UNAVAILABLE,
}

data class TimelinePlaceVisit(
    /**
     * Stable identifier for the visit, using the place id when available or a coordinate key.
     */
    val id: String,
    /**
     * Display name for the place or rounded coordinates when no name is available.
     */
    val name: String,
    /**
     * Latitude in decimal degrees, when available.
     */
    val latitude: Double? = null,
    /**
     * Longitude in decimal degrees, when available.
     */
    val longitude: Double? = null,
)

data class DayPart(
    /**
     * The time of day this part covers. The screen chooses the words for it.
     */
    val period: DayPartPeriod,
    /**
     * Preview taken from the part's own content: the first written note, or the names of the
     * places it happened at. Null when the part has neither; [captureCount] describes it then.
     */
    val description: String? = null,
    /**
     * How many captures fall in this part of the day.
     */
    val captureCount: Int,
    /**
     * Optional image URI used to visually represent this part of the day.
     */
    val featuredGraphicUri: String?,
)

/**
 * The stretch of the day a [DayPart] covers, in local time.
 */
enum class DayPartPeriod {
    /** Midnight to 6 a.m. */
    DAWN,

    /** 6 a.m. to noon. */
    MORNING,

    /** Noon to 5 p.m. */
    AFTERNOON,

    /** 5 p.m. to 10 p.m. */
    EVENING,

    /** 10 p.m. to midnight. */
    LATE,
}

enum class TimelineSortOrder {
    CHRONOLOGICAL,
    REVERSE_CHRONOLOGICAL,
}

/**
 * A use case to transform journal entries for a single day into a TimelineDay.
 */
class GetTimelineDayUseCase(
    private val summarizeJournalEntriesUseCase: SummarizeJournalEntriesUseCase,
    private val getMediaUrisUseCase: GetMediaUrisUseCase,
    private val extractPeopleUseCase: ExtractPeopleUseCase,
    private val inferMomentsUseCase: InferMomentsUseCase,
) : TimelineDayBuilder {
    /**
     * Creates a TimelineDay from journal entries for a specific date.
     *
     * @param date The date for which to create the TimelineDay
     * @param entries The journal entries for the date
     * @param events Events that overlap this day, pre-filtered by the caller.
     * @return A TimelineDay object representing the aggregated data for the day
     */
    override suspend operator fun invoke(
        date: LocalDate,
        entries: List<JournalNote>,
        events: List<Event>,
    ): TimelineDay {
        val summaryResult = summarizeJournalEntriesUseCase(entries)
        val summary = (summaryResult as? SummarizeJournalEntriesResult.Success)?.summary.orEmpty()
        val summaryUnavailableReason =
            when (summaryResult) {
                SummarizeJournalEntriesResult.NetworkUnavailable -> TimelineSummaryUnavailableReason.NO_NETWORK
                is SummarizeJournalEntriesResult.Success -> null
                SummarizeJournalEntriesResult.SummaryUnavailable -> TimelineSummaryUnavailableReason.UNAVAILABLE
            }

        val textEntries = entries.filterIsInstance<JournalNote.Text>()
        val inputLength = textEntries.sumOf { it.content.length.toLong() } + (textEntries.size - 1).coerceAtLeast(0)
        val people =
            if (inputLength > MAX_AI_INPUT_CHARACTERS) {
                emptyList()
            } else {
                when (
                    val peopleResult =
                        extractPeopleUseCase(
                            documentId = "people_summary_" + date.toEpochDays().toString(),
                            text = textEntries.joinToString("\n") { it.content },
                        )
                ) {
                    is AIResult.Success -> peopleResult.value
                    is AIResult.Unavailable -> emptyList()
                    is AIResult.Error -> emptyList()
                }
            }

        // Keep the media lookup warm for downstream screens even though the list cards rely on raw notes.
        getMediaUrisUseCase(date)

        val placesVisited = extractPlacesVisited(entries)
        val moments = inferMomentsUseCase(date, entries, placesVisited)

        return TimelineDay(
            tldr = summary,
            date = date,
            start = entries.minOf { entry -> entry.creationTimestamp },
            end = entries.maxOf { it.creationTimestamp },
            people = people,
            events = events,
            placesVisited = placesVisited,
            moments = moments,
            parts = extractDayParts(entries),
            entries = entries.sortedByDescending { entry -> entry.creationTimestamp },
            summaryUnavailableReason = summaryUnavailableReason,
        )
    }
}

/**
 * Builds a fully enriched [TimelineDay] from a grouped set of journal entries.
 *
 * @param date Calendar date represented by [entries].
 * @param entries Journal entries grouped into the day.
 * @param events Events that overlap the day's activity bounds.
 */
fun interface TimelineDayBuilder {
    suspend operator fun invoke(
        date: LocalDate,
        entries: List<JournalNote>,
        events: List<Event>,
    ): TimelineDay
}

internal fun extractPlacesVisited(entries: List<JournalNote>): List<TimelinePlaceVisit> =
    entries
        .mapNotNull { note -> note.location?.toTimelinePlaceVisit() }
        .distinctBy(TimelinePlaceVisit::id)

internal fun extractDayParts(entries: List<JournalNote>): List<DayPart> {
    val groupedEntries =
        entries
            .sortedBy { entry -> entry.creationTimestamp }
            .groupBy { entry -> entry.creationTimestamp.toDayPartPeriod() }

    return DayPartPeriod.entries.mapNotNull { period ->
        val bucketEntries = groupedEntries[period].orEmpty()
        if (bucketEntries.isEmpty()) {
            null
        } else {
            val textPreview =
                bucketEntries
                    .filterIsInstance<JournalNote.Text>()
                    .firstOrNull()
                    ?.content
                    ?.boundedDayPreview()
            val locationPreview =
                bucketEntries
                    .mapNotNull { entry -> entry.location?.displayName }
                    .distinct()
                    .take(2)
                    .joinToString(" • ")
                    .ifBlank { null }

            DayPart(
                period = period,
                description = textPreview ?: locationPreview,
                captureCount = bucketEntries.size,
                featuredGraphicUri = bucketEntries.firstVisualUri(),
            )
        }
    }
}

private fun NoteLocation.toTimelinePlaceVisit(): TimelinePlaceVisit? {
    val latitude = effectiveLatitude ?: return null
    val longitude = effectiveLongitude ?: return null
    val coordinateKey = "${roundCoordinate(latitude)},${roundCoordinate(longitude)}"
    return TimelinePlaceVisit(
        id = place?.id?.toString() ?: coordinateKey,
        name = displayName ?: "${roundCoordinate(latitude)}, ${roundCoordinate(longitude)}",
        latitude = latitude,
        longitude = longitude,
    )
}

private fun roundCoordinate(value: Double): Double = round(value * 10_000) / 10_000

private fun Instant.toDayPartPeriod(): DayPartPeriod {
    val hour = toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (hour) {
        in 0..5 -> DayPartPeriod.DAWN
        in 6..11 -> DayPartPeriod.MORNING
        in 12..16 -> DayPartPeriod.AFTERNOON
        in 17..21 -> DayPartPeriod.EVENING
        else -> DayPartPeriod.LATE
    }
}

private fun List<JournalNote>.firstVisualUri(): String? =
    firstNotNullOfOrNull { entry ->
        when (entry) {
            is JournalNote.Image -> entry.mediaRef
            is JournalNote.Video -> entry.mediaRef
            is JournalNote.Audio -> null
            is JournalNote.Text -> null
        }
    }

private fun String.boundedDayPreview(): String {
    val start = indexOfFirst { !it.isWhitespace() }
    if (start == -1) return ""
    val end = indexOfLast { !it.isWhitespace() }
    return substring(start, start + minOf(120, end - start + 1))
}
