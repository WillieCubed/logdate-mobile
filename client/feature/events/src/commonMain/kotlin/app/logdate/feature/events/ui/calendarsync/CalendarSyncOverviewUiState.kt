package app.logdate.feature.events.ui.calendarsync

import app.logdate.feature.events.ui.settings.RelativeAge
import kotlin.time.Instant

/**
 * UI state for the calendar sync overview screen.
 *
 * The screen renders a permission state machine first (grant → enable → run / activity),
 * then a status card with the most recent worker run, then two ListItem rows for the
 * sub-screens. [permissionState] is the input that selects which branch the composable
 * draws.
 */
data class CalendarSyncOverviewUiState(
    val permissionState: PermissionState = PermissionState.Unknown,
    val isSyncEnabled: Boolean = false,
    val selectedCalendarCount: Int = 0,
    val totalCalendarCount: Int = 0,
    val lastRunAt: Instant? = null,
    val lastRunAge: RelativeAge? = null,
    val lastCreatedCount: Int = 0,
    val lastUpdatedCount: Int = 0,
    val lastError: String? = null,
    val isRunInFlight: Boolean = false,
)

/**
 * Coarse permission state the overview screen renders branches for. Permanently-denied
 * is collapsed into [Denied] for now — distinguishing the two requires Android-specific
 * `shouldShowRequestPermissionRationale` plumbing that can land later if it turns out
 * the deep-link-to-settings affordance is actually needed.
 */
enum class PermissionState {
    Unknown,
    Granted,
    Denied,
}
