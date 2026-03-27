package app.logdate.ui.search

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Display state for a single entry search result.
 *
 * Feature modules map their domain/repository models into this shape,
 * then pass it to [EntrySearchResultItem] for rendering.
 */
data class EntrySearchResultUiState(
    val id: String,
    val content: String,
    val dateLabel: String,
    val typeLabel: String,
    val typeIcon: ImageVector,
)
