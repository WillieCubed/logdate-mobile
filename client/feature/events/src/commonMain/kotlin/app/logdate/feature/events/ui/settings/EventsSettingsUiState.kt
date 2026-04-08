package app.logdate.feature.events.ui.settings

/**
 * UI state for the events hub screen. Holds the two user-facing toggles: whether LogDate
 * notices events at all, and whether the names it picks are descriptive or simple.
 */
data class EventsSettingsUiState(
    val isAutoEventsEnabled: Boolean = true,
    val isSmartNamingEnabled: Boolean = true,
)
