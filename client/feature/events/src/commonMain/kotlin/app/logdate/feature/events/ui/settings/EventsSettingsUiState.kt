package app.logdate.feature.events.ui.settings

import app.logdate.client.domain.events.EventInferenceSensitivity
import kotlin.time.Instant

/**
 * UI state for the auto-events settings screen.
 *
 * Holds the master toggle, sensitivity selection, naming preference, and the most recent
 * worker run snapshot. Sourced from `LogdatePreferencesDataSource` so the screen reflects
 * the same state as the background worker, with no extra repository in between.
 */
data class EventsSettingsUiState(
    val isAutoEventsEnabled: Boolean = false,
    val sensitivity: EventInferenceSensitivity = EventInferenceSensitivity.MEDIUM,
    val isSmartNamingEnabled: Boolean = true,
    val lastRunAt: Instant? = null,
    /**
     * Coarse "just now / N min ago / Nh ago / Nd ago" age of [lastRunAt], refreshed once a
     * minute by the ViewModel's ticker so the screen never shows a stale label while left
     * open. `null` whenever [lastRunAt] is `null`. The Composable maps the variant to a
     * localized `stringResource`.
     */
    val lastRunAge: RelativeAge? = null,
    val lastCreatedCount: Int = 0,
    val recentCreatedCount: Int = 0,
    val lastError: String? = null,
    val isRunInFlight: Boolean = false,
)

/**
 * Coarse age bucket for the auto-events status card. Stays in the UI state instead of being
 * formatted in the ViewModel so the Composable can pick a localized format string at render
 * time without the VM needing access to compose resources.
 */
sealed interface RelativeAge {
    data object JustNow : RelativeAge

    data class Minutes(
        val count: Long,
    ) : RelativeAge

    data class Hours(
        val count: Long,
    ) : RelativeAge

    data class Days(
        val count: Long,
    ) : RelativeAge
}
