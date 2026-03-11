package app.logdate.ui.timeline

import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import kotlin.time.Instant

private val hiddenSummaryValues =
    setOf(
        "",
        "No summary available.",
        "Summary currently not available.",
    )

fun createTimelineDayUiState(
    summary: String,
    date: kotlinx.datetime.LocalDate,
    people: List<PersonUiState> = emptyList(),
    events: List<String> = emptyList(),
    placesVisited: List<PlaceUiState> = emptyList(),
    mediaUris: List<MediaObjectUiState> = emptyList(),
    notes: List<NoteUiState> = emptyList(),
    isLoadingSummary: Boolean = false,
    isLoadingPeople: Boolean = false,
): TimelineDayUiState {
    val sortedNotes = notes.sortedByDescending { note -> note.timestamp() }
    val visuals = sortedNotes.visualNotes()
    val audioNotes = sortedNotes.filterIsInstance<AudioNoteUiState>()
    val textNotes = sortedNotes.filterIsInstance<TextNoteUiState>()

    val layout =
        when {
            visuals.isNotEmpty() -> TimelineDayCardLayout.MEDIA_LED
            audioNotes.isNotEmpty() -> TimelineDayCardLayout.VOICE_LED
            placesVisited.size >= 2 -> TimelineDayCardLayout.PLACE_LED
            else -> TimelineDayCardLayout.STORY_LED
        }

    val heroSection =
        when (layout) {
            TimelineDayCardLayout.MEDIA_LED ->
                visuals
                    .takeIf { it.isNotEmpty() }
                    ?.let { mediaNotes ->
                        TimelineMediaSectionUiState(
                            label = "Captured",
                            items = mediaNotes.take(3).map(VisualNoteUiState::toMediaItem),
                        )
                    }
            TimelineDayCardLayout.VOICE_LED ->
                audioNotes.firstOrNull()?.let { note ->
                    TimelineAudioSectionUiState(
                        label = "Heard back",
                        note = note,
                    )
                }
            TimelineDayCardLayout.PLACE_LED ->
                placesVisited
                    .takeIf { it.isNotEmpty() }
                    ?.let { places ->
                        TimelinePlaceSectionUiState(
                            label = "Went through",
                            places = places.take(3),
                        )
                    }
            TimelineDayCardLayout.STORY_LED ->
                textNotes.firstOrNull()?.let { note ->
                    TimelineTextSnippetSectionUiState(
                        label = "From the day",
                        text = note.text.toSnippet(),
                        timestamp = note.timestamp,
                    )
                }
        }

    val supportCandidates =
        buildList {
            if (layout != TimelineDayCardLayout.STORY_LED) {
                textNotes.firstOrNull()?.let { note ->
                    add(
                        TimelineTextSnippetSectionUiState(
                            label = "Noted",
                            text = note.text.toSnippet(),
                            timestamp = note.timestamp,
                        ),
                    )
                }
            } else {
                textNotes.drop(1).firstOrNull()?.let { note ->
                    add(
                        TimelineTextSnippetSectionUiState(
                            label = "Held onto",
                            text = note.text.toSnippet(),
                            timestamp = note.timestamp,
                        ),
                    )
                }
            }

            if (layout != TimelineDayCardLayout.PLACE_LED && placesVisited.isNotEmpty()) {
                add(
                    TimelinePlaceSectionUiState(
                        label = "Moved through",
                        places = placesVisited.take(3),
                    ),
                )
            }

            if (layout != TimelineDayCardLayout.VOICE_LED) {
                audioNotes.firstOrNull()?.let { note ->
                    add(
                        TimelineAudioSectionUiState(
                            label = "Said out loud",
                            note = note,
                        ),
                    )
                }
            }

            if (layout != TimelineDayCardLayout.MEDIA_LED && visuals.isNotEmpty()) {
                add(
                    TimelineMediaSectionUiState(
                        label = "Captured",
                        items = visuals.take(2).map(VisualNoteUiState::toMediaItem),
                    ),
                )
            }
        }

    val supportingSections =
        supportCandidates
            .filterNot { section -> section == heroSection }
            .take(2)

    return TimelineDayUiState(
        summary = summary,
        supportingSummary = summary.takeIf { it !in hiddenSummaryValues },
        date = date,
        people = people,
        events = events,
        placesVisited = placesVisited,
        mediaUris = mediaUris,
        notes = sortedNotes,
        layout = layout,
        recap =
            TimelineDayRecapUiState(
                captureCount = sortedNotes.size,
                mediaCount = visuals.size,
                audioCount = audioNotes.size,
                placeCount = placesVisited.size,
                peopleCount = people.size,
                activeSpanMinutes = sortedNotes.activeSpanMinutes(),
            ),
        heroSection = heroSection,
        supportingSections = supportingSections,
        isLoadingSummary = isLoadingSummary,
        isLoadingPeople = isLoadingPeople,
    )
}

private fun NoteUiState.timestamp(): Instant =
    when (this) {
        is AudioNoteUiState -> timestamp
        is ImageNoteUiState -> timestamp
        is TextNoteUiState -> timestamp
        is VideoNoteUiState -> timestamp
    }

private fun List<NoteUiState>.activeSpanMinutes(): Int {
    val earliest = minOfOrNull { note -> note.timestamp() } ?: return 0
    val latest = maxOfOrNull { note -> note.timestamp() } ?: return 0
    return (latest - earliest).inWholeMinutes.toInt()
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

private fun VisualNoteUiState.toMediaItem(): TimelineMediaItemUiState =
    TimelineMediaItemUiState(
        uri = uri,
        isVideo = isVideo,
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
