package app.logdate.client.health

import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Repository interface for sleep-related health data.
 * This handles accessing sleep sessions and related data.
 */
interface SleepRepository {
    /**
     * Checks if the app has permissions to access sleep data.
     * 
     * @return true if sleep permissions are granted, false otherwise
     */
    suspend fun hasSleepPermissions(): Boolean
    
    /**
     * Requests permissions to access sleep data.
     * Note: This is a placeholder for the actual permission flow, which
     * would be handled by a UI component in a real implementation.
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
    suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession>
    
    /**
     * Gets average wake-up time based on sleep data.
     * 
     * @param timeZone The time zone to use for calculations
     * @param days Number of days to look back for data
     * @return The average wake-up time, or null if insufficient data
     */
    suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
    
    /**
     * Gets average sleep time based on sleep data.
     * 
     * @param timeZone The time zone to use for calculations
     * @param days Number of days to look back for data
     * @return The average sleep time, or null if insufficient data
     */
    suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
}