package app.logdate.client.domain.events

import app.logdate.client.datastore.LogdatePreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * How willing the on-device event inference is to materialize a candidate cluster as an event.
 *
 * The threshold is the minimum total signal count (photos + notes) a cluster must contain on
 * top of its location stop. Lower values create more events from less evidence; higher values
 * stay quiet until activity is unambiguous.
 */
enum class EventInferenceSensitivity(
    val signalThreshold: Int,
) {
    /** Two signals (cluster needs 2 photos/notes alongside the stop). Conservative default. */
    LOW(signalThreshold = 4),

    /** One signal pair, the project's general default. */
    MEDIUM(signalThreshold = 2),

    /** Single signal — even one photo at a new place is enough. */
    HIGH(signalThreshold = 1),

    ;

    companion object {
        /**
         * Decodes the persisted preference string into the typed enum, falling back to
         * [MEDIUM] for unknown values. Centralized so the worker and the settings ViewModel
         * can't drift on the same parse.
         */
        fun fromPreference(stored: String): EventInferenceSensitivity = runCatching { valueOf(stored) }.getOrDefault(MEDIUM)
    }
}

/**
 * Typed view over [LogdatePreferencesDataSource.observeEventInferenceSensitivity]. Lives
 * here because the data store sits below the domain module and can't depend on this enum
 * directly without a cycle, but every consumer of the preference is in domain or above.
 */
fun LogdatePreferencesDataSource.observeEventInferenceSensitivityValue(): Flow<EventInferenceSensitivity> =
    observeEventInferenceSensitivity().map(EventInferenceSensitivity::fromPreference)
