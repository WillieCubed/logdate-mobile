package app.logdate.ui.timeline

import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import app.logdate.util.now
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

data class HomeTimelineUiState(
    val items: List<TimelineDayUiState> = emptyList(),
    val selectedItem: TimelineDaySelection = TimelineDaySelection.NotSelected,
    val selectedDay: TimelineDayUiState? = null,
    val showEmptyState: Boolean = false,
    val isLoading: Boolean = false,
    val loadingState: TimelineLoadingState = TimelineLoadingState.Loaded,
    val timelineSuggestion: TimelineSuggestionBlock? = null,
    val snackbarMessage: String? = null,
)

data class TimelineDayUiState(
    val summary: String,
    val date: LocalDate = LocalDate.now(), // TODO: Don't use default value here
    val people: List<PersonUiState> = DEMO_PEOPLE,
    val events: List<String> = emptyList(), // TODO: Actually include events
    val placesVisited: List<PlaceUiState> = DEMO_PLACES_VISITED, // TODO: Actually include places visited
    val mediaUris: List<MediaObjectUiState> = emptyList(), // TODO: Actually include media
    val notes: List<NoteUiState> = emptyList(),
    val isLoadingSummary: Boolean = false,
    val isLoadingPeople: Boolean = false,
)

enum class TimelineLoadingState {
    /** Initial state - show skeleton UI immediately */
    InitialLoading,
    /** Loading content - show placeholders with shimmer */
    LoadingContent,
    /** Fully loaded */
    Loaded,
    /** Error state */
    Error
}

val DEMO_PLACES_VISITED = listOf(
    PlaceUiState(Uuid.random(), "Home"),
    PlaceUiState(Uuid.random(), "Work"),
    PlaceUiState(Uuid.random(), "Jamie's House"),
)

val DEMO_PEOPLE = listOf(
    PersonUiState(Uuid.random(), "Jamie"),
    PersonUiState(Uuid.random(), "Alice"),
)

sealed interface NoteUiState

data class TextNoteUiState(
    val noteId: Uuid,
    val text: String,
    val timestamp: Instant,
) : NoteUiState

data class ImageNoteUiState(
    val noteId: Uuid,
    val uri: String,
    val timestamp: Instant,
) : NoteUiState

data class AudioNoteUiState(
    val noteId: Uuid,
    val uri: String,
    val timestamp: Instant,
    val duration: Long = 0 // Duration in milliseconds
) : NoteUiState

data class VideoNoteUiState(
    val noteId: Uuid,
    val uri: String,
    val timestamp: Instant,
    val thumbnailUri: String? = null,
    val duration: Long = 0 // Duration in milliseconds
) : NoteUiState