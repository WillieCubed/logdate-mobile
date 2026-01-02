package app.logdate.feature.rewind.ui

import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Base interface for all rewind panel UI states.
 * 
 * This sealed interface represents the various types of panels that can be displayed
 * in a rewind story, each with specific content and display requirements.
 */
sealed interface RewindPanelUiState {
    // Common properties could be added here in the future
}

/**
 * UI state for a panel with basic text content.
 * 
 * Represents a simple panel with centered text and customizable background.
 * Ideal for introductory or concluding panels in a rewind story.
 *
 * @property text The main text content to display
 * @property background Background styling information
 */
data class BasicTextRewindPanelUiState(
    val text: String,
    val background: RewindPanelBackgroundSpec = RewindPanelBackgroundSpec()
) : RewindPanelUiState

/**
 * UI state for a panel with title and subtitle.
 * 
 * Represents a panel with hierarchical text content - a prominent title and
 * supporting subtitle text. Optional background image for visual interest.
 *
 * @property title The primary headline text
 * @property subtitle The supporting descriptive text
 * @property backgroundUri Optional URI for a background image
 */
data class SubtitledRewindPanelUiState(
    val title: String,
    val subtitle: String,
    val backgroundUri: String? = null
) : RewindPanelUiState

/**
 * UI state for a panel displaying a significant statistic.
 * 
 * Represents a panel highlighting an important numerical value with context.
 * Designed for metrics and achievements in the rewind story.
 *
 * @property title Contextual title for the statistic
 * @property statistic The numerical value to display prominently
 * @property units Units or label for the statistic (e.g., "steps", "miles")
 * @property description Additional context or interpretation of the statistic
 * @property background Background styling information
 */
data class BigStatisticRewindPanelUiState(
    val title: String,
    val statistic: String,
    val units: String,
    val description: String,
    val background: RewindPanelBackgroundSpec = RewindPanelBackgroundSpec()
) : RewindPanelUiState

/**
 * UI state for a panel displaying a text note.
 * 
 * Represents a panel showing journal text content with date information.
 *
 * @property sourceId Unique identifier of the original note
 * @property timestamp When the note was created 
 * @property content The text content from the journal note
 * @property dateFormatted Formatted date string showing when the note was created
 * @property background Background styling information
 */
data class TextNoteRewindPanelUiState(
    val sourceId: Uuid,
    val timestamp: Instant,
    val content: String,
    val dateFormatted: String,
    val background: RewindPanelBackgroundSpec = RewindPanelBackgroundSpec(
        color = 0xFF1A1A1A // Dark gray background by default
    )
) : RewindPanelUiState

/**
 * UI state for a panel displaying an image note.
 * 
 * Represents a panel showing a photo with optional caption and date information.
 *
 * @property sourceId Unique identifier of the original image
 * @property timestamp When the image was created
 * @property imageUri URI to the image resource
 * @property caption Optional caption text for the image
 * @property dateFormatted Formatted date string showing when the image was captured
 */
data class ImageRewindPanelUiState(
    val sourceId: Uuid,
    val timestamp: Instant,
    val imageUri: String,
    val caption: String? = null,
    val dateFormatted: String
) : RewindPanelUiState

/**
 * UI state for a narrative context panel.
 *
 * Sets the scene and provides context for the rewind story. Used to establish
 * the overall narrative or introduce thematic segments.
 *
 * Example: "You finally made it to the California coast"
 *
 * @property sourceId Unique identifier of the original content
 * @property timestamp When this narrative context was created
 * @property contextText The narrative text that sets the scene
 * @property backgroundImageUri Optional background image URI
 */
data class NarrativeContextRewindPanelUiState(
    val sourceId: Uuid,
    val timestamp: Instant,
    val contextText: String,
    val backgroundImageUri: String? = null
) : RewindPanelUiState

/**
 * UI state for a transition panel.
 *
 * Connects story beats and explains thematic shifts in the narrative.
 * These panels create flow by bridging different moments.
 *
 * Example: "But evenings shifted to culinary adventures"
 *
 * @property sourceId Unique identifier of the original content
 * @property timestamp When this transition was created
 * @property transitionText The transition text connecting story beats
 */
data class TransitionRewindPanelUiState(
    val sourceId: Uuid,
    val timestamp: Instant,
    val transitionText: String
) : RewindPanelUiState

/**
 * Background styling information for rewind panels.
 *
 * Defines the visual appearance of a panel's background, supporting either
 * solid colors or image backgrounds.
 *
 * @property color Optional hex color value, null for default theme color
 * @property uri Optional URI to an image resource for background
 */
data class RewindPanelBackgroundSpec(
    val color: Long? = null,
    val uri: String? = null
)

/**
 * Whether the panel should use a background image.
 */
val RewindPanelBackgroundSpec.useBackgroundImage
    get() = uri != null