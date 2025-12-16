package app.logdate.feature.rewind.ui

import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * UI state for an image note in a Rewind story panel.
 *
 * Represents an image from the user's journal that has been selected for inclusion
 * in a weekly rewind. Includes the image URI and metadata for display.
 *
 * @param sourceId Unique identifier of the original note
 * @param timestamp When the image was captured
 * @param imageUri URI of the image to display
 * @param caption Optional user-provided caption for the image
 * @param dateFormatted The formatted date string for display (e.g., "Tuesday, Nov 19")
 */
data class ImageNoteRewindPanelUiState(
    val sourceId: Uuid,
    val timestamp: Instant,
    val imageUri: String,
    val caption: String? = null,
    val dateFormatted: String
) : RewindPanelUiState