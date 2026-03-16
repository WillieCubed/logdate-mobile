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
        val locationDisplayName: String? = null,
        val journals: List<JournalReference> = emptyList(),
        val exif: ExifDisplayData? = null,
    ) : MediaDetailUiState

    data class VideoContent(
        val noteId: Uuid,
        val mediaRef: String,
        val createdAt: Instant,
        val location: NoteLocation?,
        val locationDisplayName: String? = null,
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

/**
 * State for presenter mode — showing media on an external display.
 */
data class PresenterState(
    val isExternalDisplayAvailable: Boolean = false,
    val isPresenting: Boolean = false,
    val currentIndex: Int = 0,
    val totalItems: Int = 0,
    val mediaItems: List<PresenterMediaItem> = emptyList(),
)

/**
 * A media item in the presenter navigation strip.
 */
data class PresenterMediaItem(
    val uid: Uuid,
    val uri: String,
    val isVideo: Boolean,
)
