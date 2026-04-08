package app.logdate.client.datastore

import kotlin.time.Instant

/**
 * Snapshot of the most recent on-device event inference worker run.
 *
 * Surfaced verbatim by the auto-events settings screen so the user can see what the
 * background pipeline is doing without having to dig through logs. The fields are
 * intentionally flat — there's no event-specific repository, the data lives next to every
 * other UI preference.
 */
data class EventInferenceStats(
    /** When the worker last ran, or `null` if it has never run on this device. */
    val lastRunAt: Instant?,
    /** How many events the most recent run created. */
    val lastCreatedCount: Int,
    /** Rolling total of events created across recent runs (settings shows "in last 30 days"). */
    val recentCreatedCount: Int,
    /** Most recent error message, or `null` if the most recent run succeeded. */
    val lastError: String?,
)
