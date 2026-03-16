package app.logdate.feature.library.ui.detail

import app.logdate.client.repository.journals.NoteLocation
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * UI state for the media detail screen.
 */
sealed interface MediaDetailUiState {
    data object Loading : MediaDetailUiState

    data class Error(
        val message: String,
    ) : MediaDetailUiState

    data class ImageContent(
        val noteId: Uuid,
        val mediaRef: String,
        val createdAt: Instant,
        val location: NoteLocation?,
        val journals: List<JournalReference> = emptyList(),
        val exif: ExifDisplayData? = null,
    ) : MediaDetailUiState

    data class VideoContent(
        val noteId: Uuid,
        val mediaRef: String,
        val createdAt: Instant,
        val location: NoteLocation?,
        val journals: List<JournalReference> = emptyList(),
    ) : MediaDetailUiState
}

/**
 * A journal that contains this media item, used for "Appears in" cross-references.
 */
data class JournalReference(
    val id: Uuid,
    val title: String,
)

/**
 * Camera metadata extracted from EXIF for display in the detail view.
 */
data class ExifDisplayData(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val aperture: Double? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val shutterSpeed: String? = null,
)
