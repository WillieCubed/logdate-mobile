package app.logdate.ui.search

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString

/**
 * Display state for a single entry search result.
 *
 * Feature modules map their domain/repository models into this shape,
 * then pass it to [EntrySearchResultItem] for rendering.
 */
data class EntrySearchResultUiState(
    val id: String,
    val contentText: AnnotatedString,
    val dateLabel: String,
    val typeLabel: String,
    val typeIcon: ImageVector,
)
