package app.logdate.feature.journals.ui.detail

import kotlin.time.Instant
import kotlin.uuid.Uuid

sealed interface JournalDetailUiState {
    data object Loading : JournalDetailUiState

    data class Success(
        val journalId: Uuid,
        val title: String,
        val entries: List<EntryDisplayData>,
        val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    ) : JournalDetailUiState

    data class Error(
        val type: String,
        val message: String,
    ) : JournalDetailUiState
}

/**
 * Defines the sort order for journal entries
 */
enum class SortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

/**
 * Represents a journal entry for display, preserving the original content type.
 */
sealed interface EntryDisplayData {
    val id: Uuid
    val timestamp: Instant

    data class TextEntry(
        override val id: Uuid,
        override val timestamp: Instant,
        val content: String,
    ) : EntryDisplayData

    data class ImageEntry(
        override val id: Uuid,
        override val timestamp: Instant,
        val mediaRef: String,
        val caption: String,
    ) : EntryDisplayData

    data class VideoEntry(
        override val id: Uuid,
        override val timestamp: Instant,
        val mediaRef: String,
        val caption: String,
    ) : EntryDisplayData

    data class AudioEntry(
        override val id: Uuid,
        override val timestamp: Instant,
        val mediaRef: String,
        val durationMs: Long,
    ) : EntryDisplayData
}
