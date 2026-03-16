package app.logdate.ui.timeline

import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import app.logdate.util.now
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class HomeTimelineUiState(
    val items: List<TimelineDayUiState> = emptyList(),
    val selectedItem: TimelineDaySelection = TimelineDaySelection.NotSelected,
    val selectedDay: TimelineDayUiState? = null,
    val showEmptyState: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreOlderContent: Boolean = false,
    val appendError: String? = null,
    val loadingState: TimelineLoadingState = TimelineLoadingState.Loaded,
    val timelineSuggestion: TimelineSuggestionBlock? = null,
    val snackbarMessage: String? = null,
)

data class TimelineDayUiState(
    val summary: String,
    val supportingSummary: String? = null,
    val date: LocalDate = LocalDate.now(), // TODO: Don't use default value here
    val people: List<PersonUiState> = emptyList(),
    val events: List<String> = emptyList(), // TODO: Actually include events
    val placesVisited: List<PlaceUiState> = emptyList(),
    val mediaUris: List<MediaObjectUiState> = emptyList(), // TODO: Actually include media
    val notes: List<NoteUiState> = emptyList(),
    val layout: TimelineDayCardLayout = TimelineDayCardLayout.STORY_LED,
    val recap: TimelineDayRecapUiState = TimelineDayRecapUiState(),
    val heroSection: TimelineDaySectionUiState? = null,
    val supportingSections: List<TimelineDaySectionUiState> = emptyList(),
    val isLoadingSummary: Boolean = false,
    val isLoadingPeople: Boolean = false,
    // Semantic Timeline fields
    val moments: List<MomentUiState> = emptyList(),
    val dayPresentation: DayPresentation = DayPresentation.FLOWING,
)

enum class TimelineDayCardLayout {
    MEDIA_LED,
    VOICE_LED,
    PLACE_LED,
    STORY_LED,
}

data class TimelineDayRecapUiState(
    val captureCount: Int = 0,
    val mediaCount: Int = 0,
    val audioCount: Int = 0,
    val placeCount: Int = 0,
    val peopleCount: Int = 0,
    val activeSpanMinutes: Int = 0,
)

sealed interface TimelineDaySectionUiState {
    val label: String
}

data class TimelineTextSnippetSectionUiState(
    override val label: String,
    val text: String,
    val timestamp: Instant,
) : TimelineDaySectionUiState

data class TimelineMediaItemUiState(
    val uri: String,
    val isVideo: Boolean = false,
)

data class TimelineMediaSectionUiState(
    override val label: String,
    val items: List<TimelineMediaItemUiState>,
) : TimelineDaySectionUiState

data class TimelineAudioSectionUiState(
    override val label: String,
    val note: AudioNoteUiState,
) : TimelineDaySectionUiState

data class TimelinePlaceSectionUiState(
    override val label: String,
    val places: List<PlaceUiState>,
) : TimelineDaySectionUiState

// region Semantic Timeline (moment-based)

/**
 * Presentation mode for a day in the Semantic Timeline.
 */
enum class DayPresentation {
    /** Moments flow inline with contextual labels as dividers. */
    FLOWING,

    /** Moments are distinct card surfaces (for birthdays, Rewinds, high-activity days). */
    STACKED,
}

/**
 * A single moment within a day — a semantically coherent experience.
 */
data class MomentUiState(
    val id: String,
    /**
     * Contextual label: "At Houndstooth Coffee", "That evening", "Morning run".
     */
    val label: String,
    /**
     * Subtle time-of-day hint: "morning", "afternoon".
     */
    val timeOfDay: String? = null,
    /**
     * Best text snippet for this moment, truncated.
     */
    val textSnippet: String? = null,
    /**
     * Photos and videos in this moment.
     */
    val media: List<MomentMediaUiState> = emptyList(),
    /**
     * Audio recording in this moment, if any.
     */
    val audio: MomentAudioUiState? = null,
    /**
     * Places associated with this moment.
     */
    val places: List<PlaceUiState> = emptyList(),
    /**
     * People mentioned in this moment.
     */
    val people: List<String> = emptyList(),
    /**
     * Whether this is the hero (most significant) moment in the day.
     */
    val isHero: Boolean = false,
)

data class MomentMediaUiState(
    val uri: String,
    val isVideo: Boolean = false,
)

data class MomentAudioUiState(
    val uri: String,
    val durationMs: Long = 0,
)

// endregion

enum class TimelineLoadingState {
    /** Initial state - show skeleton UI immediately */
    InitialLoading,

    /** Loading content - show placeholders with shimmer */
    LoadingContent,

    /** Fully loaded */
    Loaded,

    /** Error state */
    Error,
}

val DEMO_PEOPLE =
    listOf(
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
    val caption: String = "",
) : NoteUiState

data class AudioNoteUiState(
    val noteId: Uuid,
    val uri: String,
    val timestamp: Instant,
    val duration: Long = 0, // Duration in milliseconds
) : NoteUiState

data class VideoNoteUiState(
    val noteId: Uuid,
    val uri: String,
    val timestamp: Instant,
    val thumbnailUri: String? = null,
    val duration: Long = 0, // Duration in milliseconds
    val caption: String = "",
) : NoteUiState
