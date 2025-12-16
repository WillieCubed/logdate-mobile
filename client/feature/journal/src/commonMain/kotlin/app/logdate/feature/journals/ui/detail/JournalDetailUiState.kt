package app.logdate.feature.journals.ui.detail

sealed interface JournalDetailUiState {
    data object Loading : JournalDetailUiState
    data class Success(
        val journalId: kotlin.uuid.Uuid,
        val title: String,
        val entries: List<EntryDisplayData>,
        val sortOrder: SortOrder = SortOrder.NEWEST_FIRST
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
    OLDEST_FIRST
}

/**
 * Represents a journal entry with its content and timestamp
 */
data class EntryDisplayData(
    val id: kotlin.uuid.Uuid,
    val content: String,
    val timestamp: kotlinx.datetime.Instant
)
