package app.logdate.feature.timeline.ui

import app.logdate.ui.profiles.PersonUiState
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class HomeTimelineUiState(
    val items: List<TimelineDayUiState> = emptyList(),
    val selectedItem: TimelineDaySelection = TimelineDaySelection.NotSelected,
)

data class MediaObjectUiState(
    val uid: String,
    val uri: String,
)

data class TimelineDayUiState(
    val summary: String,
    val date: LocalDate,
    val people: List<PersonUiState> = emptyList(),
    val events: List<String> = emptyList(), // TODO: Actually include events
    val placesVisited: List<String> = emptyList(), // TODO: Actually include places visited
    val mediaUris: List<MediaObjectUiState> = emptyList(), // TODO: Actually include media
    val notes: List<NoteUiState> = emptyList(),
)

sealed interface NoteUiState

data class TextNoteUiState(
    val noteId: String,
    val text: String,
    val timestamp: Instant,
) : NoteUiState

data class ImageNoteUiState(
    val noteId: String,
    val uri: String,
    val timestamp: Instant,
) : NoteUiState