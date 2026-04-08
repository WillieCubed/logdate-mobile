package app.logdate.feature.events.ui.calendarsync

/**
 * UI state for the calendar sync overview screen: a permission state machine, the master
 * toggle, and the count of selected calendars (rendered as "N of M selected" on the
 * picker row).
 */
data class CalendarSyncOverviewUiState(
    val permissionState: PermissionState = PermissionState.Unknown,
    val isSyncEnabled: Boolean = false,
    val selectedCalendarCount: Int = 0,
    val totalCalendarCount: Int = 0,
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
