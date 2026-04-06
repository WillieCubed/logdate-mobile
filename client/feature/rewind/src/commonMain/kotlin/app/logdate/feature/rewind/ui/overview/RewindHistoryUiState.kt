package app.logdate.feature.rewind.ui.overview

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * UI state representing a historical rewind entry for display in the overview screen.
 *
 * @param uid The unique identifier of the underlying domain rewind entity
 * @param title The human-readable title/name assigned to this rewind
 * @param label Short descriptive label for the time period (e.g., "Week 42")
 * @param startDate Start date of the rewind period
 * @param endDate End date of the rewind period
 */
data class RewindHistoryUiState(
    val uid: Uuid,
    val title: String,
    val label: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)
