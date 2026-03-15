package app.logdate.feature.library.ui

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * UI state for the Library screen.
 */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data object PermissionRequired : LibraryUiState

    data object Empty : LibraryUiState

    data class Content(
        val groups: List<LibraryGridGroup>,
        val totalCount: Int,
        val isRefreshing: Boolean = false,
    ) : LibraryUiState
}

/**
 * A group of media items sharing the same month, displayed under a date header in the grid.
 */
data class LibraryGridGroup(
    val label: String,
    val items: List<LibraryMediaItem>,
)

/**
 * A single media item in the library grid.
 */
data class LibraryMediaItem(
    val uid: Uuid,
    val uri: String,
    val thumbnailUri: String?,
    val isVideo: Boolean,
    val timestamp: Instant,
)
