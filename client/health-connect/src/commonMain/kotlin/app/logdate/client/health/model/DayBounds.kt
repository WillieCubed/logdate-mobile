package app.logdate.client.health.model

import kotlinx.datetime.Instant

/**
 * Represents the bounds of a day, as determined by sleep patterns or user preferences.
 * This is used to define when a "day" starts and ends for a user, which may not align
 * with midnight-to-midnight.
 */
data class DayBounds(
    val start: Instant,
    val end: Instant
)