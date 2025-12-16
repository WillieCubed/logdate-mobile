package app.logdate.feature.rewind.ui.overview

import kotlin.uuid.Uuid

/**
 * UI state representing a historical rewind entry for display in the overview screen.
 * 
 * This lightweight data class represents past rewinds that are available for viewing.
 * It contains the minimal information needed to display rewind cards in the scrollable
 * list and handle user interactions.
 * 
 * ## Usage Context:
 * Used primarily in the floating card list to display past weekly rewinds. Each
 * instance represents a completed rewind that users can tap to view in detail.
 * 
 * ## Data Flow:
 * - Created by `RewindOverviewViewModel` from domain layer rewind data
 * - Consumed by `RewindScreenContent` for UI rendering
 * - Used to construct `RewindPreviewUiState` for card display
 * 
 * ## Future Enhancements:
 * Could be extended to include:
 * - Thumbnail images for visual previews
 * - Activity summaries or highlight counts
 * - Date ranges for better temporal context
 * - Completion status or processing metadata
 * 
 * @param uid The unique identifier of the underlying domain rewind entity
 * @param title The human-readable title/name assigned to this rewind
 */
data class RewindHistoryUiState(
    /**
     * The UID of the [app.logdate.model.Rewind] this state represents.
     */
    val uid: Uuid,
    /**
     * The title of the [app.logdate.model.Rewind] this state represents.
     */
    val title: String,
)