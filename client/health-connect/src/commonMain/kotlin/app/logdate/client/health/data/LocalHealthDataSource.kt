package app.logdate.client.health.data

import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Interface for accessing locally stored health data.
 * This provides access to cached health data when the platform APIs are unavailable
 * or when offline.
 */
interface LocalHealthDataSource {
    /**
     * Retrieves sleep sessions within the specified time range from local storage.
     * 
     * @param start The start time of the range
     * @param end The end time of the range
     * @return List of sleep sessions within the range
     */
    suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession>
    
    /**
     * Gets average wake-up time from locally stored sleep data.
     * 
     * @param timeZone The time zone to use for calculations
     * @param days Number of days to look back for data
     * @return The average wake-up time, or null if insufficient data
     */
    suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
    
    /**
     * Gets average sleep time from locally stored sleep data.
     * 
     * @param timeZone The time zone to use for calculations
     * @param days Number of days to look back for data
     * @return The average sleep time, or null if insufficient data
     */
    suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
    
    /**
     * Saves sleep sessions to local storage.
     * 
     * @param sessions The sleep sessions to save
     * @return true if sessions were saved successfully, false otherwise
     */
    suspend fun saveSleepSessions(sessions: List<SleepSession>): Boolean
    
    /**
     * Gets day bounds for a specific date from local storage.
     * 
     * @param date The date to get bounds for
     * @param timeZone The time zone to use for calculations
     * @return DayBounds containing start and end Instants for the day
     */
    suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds
    
    /**
     * Saves day bounds for a specific date to local storage.
     * 
     * @param date The date to save bounds for
     * @param bounds The day bounds to save
     * @param timeZone The time zone used for calculations
     * @return true if bounds were saved successfully, false otherwise
     */
    suspend fun saveDayBoundsForDate(date: LocalDate, bounds: DayBounds, timeZone: TimeZone): Boolean
}