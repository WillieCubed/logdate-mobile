package app.logdate.ui.timeline

import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private val hiddenSummaryValues =
    setOf(
        "",
        "No summary available.",
        "Summary currently not available.",
    )

private const val STACKED_MOMENT_THRESHOLD = 5
private const val STACKED_NOTE_THRESHOLD = 10

/**
 * Creates a [TimelineDayUiState] for the Semantic Timeline.
 *
 * Moments are the sole content. The [moments] parameter should already be mapped to
 * [MomentUiState] by the caller (typically the ViewModel, which has access to domain types).
 *
 * When the caller has no moments to supply — moment inference returns nothing for a day
 * without entries — a single unlabeled moment is synthesized from the day's notes and places
 * so that the timeline has exactly one way to render a day.
 */
fun createSemanticTimelineDayUiState(
    summary: String,
    date: LocalDate,
    moments: List<MomentUiState>,
    people: List<PersonUiState> = emptyList(),
    notes: List<NoteUiState> = emptyList(),
    placesVisited: List<PlaceUiState> = emptyList(),
    events: List<DayEventUiState> = emptyList(),
    isBirthday: Boolean = false,
    isLoadingSummary: Boolean = false,
    isLoadingPeople: Boolean = false,
): TimelineDayUiState {
    val sortedNotes = notes.sortedByDescending { note -> note.timestamp() }
    val visuals = sortedNotes.visualNotes()
    val visualMedia =
        visuals.map { visual ->
            MediaObjectUiState(
                uid = visual.uri,
                uri = visual.uri,
            )
        }

    val resolvedMoments =
        moments.ifEmpty {
            listOfNotNull(synthesizeMoment(date, sortedNotes, placesVisited))
        }

    val dayPresentation =
        when {
            isBirthday -> DayPresentation.STACKED
            resolvedMoments.size >= STACKED_MOMENT_THRESHOLD -> DayPresentation.STACKED
            sortedNotes.size >= STACKED_NOTE_THRESHOLD -> DayPresentation.STACKED
            else -> DayPresentation.FLOWING
        }

    return TimelineDayUiState(
        summary = summary,
        supportingSummary = summary.takeIf { it !in hiddenSummaryValues },
        date = date,
        people = people,
        events = events,
        placesVisited = placesVisited,
        mediaUris = visualMedia,
        notes = sortedNotes,
        layout = resolvedMoments.toDayCardLayout(placesVisited),
        moments = resolvedMoments,
        dayPresentation = dayPresentation,
        isLoadingSummary = isLoadingSummary,
        isLoadingPeople = isLoadingPeople,
    )
}

/**
 * Picks the accent treatment for a day from what that day actually held, so a run of days
 * reads as varied rather than uniform. Priority matches the order a day is most recognizable
 * by: what it looked like, then what it sounded like, then where it happened.
 */
private fun List<MomentUiState>.toDayCardLayout(placesVisited: List<PlaceUiState>): TimelineDayCardLayout =
    when {
        any { moment -> moment.media.isNotEmpty() } -> TimelineDayCardLayout.MEDIA_LED
        any { moment -> moment.audio != null } -> TimelineDayCardLayout.VOICE_LED
        placesVisited.size >= 2 -> TimelineDayCardLayout.PLACE_LED
        else -> TimelineDayCardLayout.STORY_LED
    }

/**
 * Builds a single moment out of a day's raw notes for days that moment inference could not
 * describe. Carries no label: there is no inferred context to report, and a label naming the
 * medium would say only what the content already shows.
 */
private fun synthesizeMoment(
    date: LocalDate,
    sortedNotes: List<NoteUiState>,
    placesVisited: List<PlaceUiState>,
): MomentUiState? {
    if (sortedNotes.isEmpty() && placesVisited.isEmpty()) {
        return null
    }

    val audioNote = sortedNotes.filterIsInstance<AudioNoteUiState>().firstOrNull()

    return MomentUiState(
        id = "synthesized-$date",
        label = "",
        textSnippet =
            sortedNotes
                .filterIsInstance<TextNoteUiState>()
                .firstOrNull()
                ?.text
                ?.toSnippet(),
        media =
            sortedNotes.visualNotes().map { visual ->
                MomentMediaUiState(uri = visual.uri, isVideo = visual.isVideo)
            },
        audio =
            audioNote?.let { note ->
                MomentAudioUiState(
                    uri = note.uri,
                    durationMs = note.duration,
                    noteId = note.noteId,
                )
            },
        places = placesVisited,
        isHero = true,
    )
}

private fun NoteUiState.timestamp(): Instant =
    when (this) {
        is AudioNoteUiState -> timestamp
        is ImageNoteUiState -> timestamp
        is TextNoteUiState -> timestamp
        is VideoNoteUiState -> timestamp
    }

private fun String.toSnippet(): String =
    trim()
        .replace('\n', ' ')
        .let { value ->
            if (value.length <= 140) {
                value
            } else {
                value.take(137).trimEnd() + "..."
            }
        }

private data class VisualNoteUiState(
    val uri: String,
    val timestamp: Instant,
    val isVideo: Boolean,
)

private val ImageNoteUiState.asVisual: VisualNoteUiState
    get() =
        VisualNoteUiState(
            uri = uri,
            timestamp = timestamp,
            isVideo = false,
        )

private val VideoNoteUiState.asVisual: VisualNoteUiState
    get() =
        VisualNoteUiState(
            uri = thumbnailUri ?: uri,
            timestamp = timestamp,
            isVideo = true,
        )

private fun List<NoteUiState>.visualNotes(): List<VisualNoteUiState> =
    buildList {
        this@visualNotes.forEach { note ->
            when (note) {
                is ImageNoteUiState -> add(note.asVisual)
                is VideoNoteUiState -> add(note.asVisual)
                else -> Unit
            }
        }
    }
