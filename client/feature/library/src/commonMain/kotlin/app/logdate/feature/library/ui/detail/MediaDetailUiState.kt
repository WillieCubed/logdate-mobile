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
    ) : MediaDetailUiState

    data class VideoContent(
        val noteId: Uuid,
        val mediaRef: String,
        val createdAt: Instant,
        val location: NoteLocation?,
    ) : MediaDetailUiState
}
