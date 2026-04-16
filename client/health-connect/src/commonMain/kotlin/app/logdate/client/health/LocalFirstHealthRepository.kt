package app.logdate.client.health

import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Main repository interface for health data, following the local-first pattern.
 * This is the primary access point for all health-related data in the application.
 */
interface LocalFirstHealthRepository : HealthDataRepository {
    /**
     * Returns the current availability state of the platform health provider.
     */
    suspend fun getHealthDataAvailability(): HealthDataAvailability

    /**
     * Checks if the app has permissions to access sleep data.
     *
     * @return true if sleep permissions are granted, false otherwise
     */
    suspend fun hasSleepPermissions(): Boolean

    /**
     * Requests permissions to access sleep data.
     *
     * @return true if permissions were granted, false otherwise
     */
    suspend fun requestSleepPermissions(): Boolean

    /**
     * Retrieves sleep sessions within the specified time range.
     *
     * @param start The start time of the range
     * @param end The end time of the range
     * @return List of sleep sessions within the range
     */
    suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ): List<SleepSession>

    /**
     * Gets average wake-up time based on sleep data.
     *
     * @param timeZone The time zone to use for calculations
     * @param days Number of days to look back for data
     * @return The average wake-up time, or null if insufficient data
     */
    suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int = 30,
    ): TimeOfDay?

    /**
     * Gets average sleep time based on sleep data.
     *
     * @param timeZone The time zone to use for calculations
     * @param days Number of days to look back for data
     * @return The average sleep time, or null if insufficient data
     */
    suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int = 30,
    ): TimeOfDay?

    /**
     * Determines the semantic day bounds for a specific date.
     *
     * A "semantic day" starts when the user wakes up and ends when they fall
     * asleep. When [sleepBasedBoundariesEnabled] is true, actual sleep session
     * data is used. When false or unavailable, falls back to user preferences
     * or a default boundary.
     *
     * @param date The calendar date to resolve bounds for
     * @param timeZone The user's current time zone
     * @param sleepBasedBoundariesEnabled Whether to query Health Connect sleep sessions
     * @return DayBounds with `start` (wake-up) and `end` (bedtime) as Instants
     */
    suspend fun getDayBoundsForDate(
        date: LocalDate,
        timeZone: TimeZone,
        sleepBasedBoundariesEnabled: Boolean = true,
    ): DayBounds
}
