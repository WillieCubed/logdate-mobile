package app.logdate.client.datastore

import kotlin.time.Instant

/**
 * Snapshot of the most recent device calendar import worker run.
 *
 * Surfaced verbatim by the calendar sync settings overview screen so the user can see
 * what the background pipeline is doing without having to dig through logs. Mirrors
 * [EventInferenceStats] in shape — both are flat data classes living next to other
 * preferences instead of behind a dedicated repository.
 */
data class DeviceCalendarSyncStats(
    /** When the worker last ran, or `null` if it has never run on this device. */
    val lastRunAt: Instant?,
    /** Events created on the most recent run. */
    val lastCreatedCount: Int,
    /** Events updated in place on the most recent run. */
    val lastUpdatedCount: Int,
    /** Most recent error message, or `null` if the most recent run succeeded. */
    val lastError: String?,
)
