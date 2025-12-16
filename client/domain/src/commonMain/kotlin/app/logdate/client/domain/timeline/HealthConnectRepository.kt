package app.logdate.client.domain.timeline

import app.logdate.client.health.model.DayBounds
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Repository interface for accessing health data through Health Connect API.
 */
interface HealthConnectRepository {
    /**
     * Determines the semantic day bounds for a specific date based on sleep data.
     * 
     * This uses sleep data to determine when a user typically wakes up and goes to sleep,
     * which helps define the "semantic day" boundaries that may not align with calendar days.
     * 
     * @param date The date to get bounds for
     * @param timeZone The user's current time zone
     * @return DayBounds containing start and end Instants for the semantic day
     */
    suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds
    
    /**
     * Checks if Health Connect API is available on the device.
     * 
     * @return true if Health Connect is available, false otherwise
     */
    suspend fun isHealthConnectAvailable(): Boolean
    
    /**
     * Checks if required permissions for sleep data are granted.
     * 
     * @return true if required permissions are granted, false otherwise
     */
    suspend fun hasSleepPermissions(): Boolean
    
    /**
     * Requests required permissions for sleep data.
     * 
     * Note: On non-Android platforms, this will always return true.
     * 
     * @return true if permissions request flow was started successfully
     */
    suspend fun requestSleepPermissions(): Boolean
}