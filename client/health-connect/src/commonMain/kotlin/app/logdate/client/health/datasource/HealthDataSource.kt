package app.logdate.client.health.datasource

import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Base interface for health data sources.
 * Defines common methods for both local and remote data sources.
 */
interface HealthDataSource {
    /**
     * Checks if this data source is available and ready to use.
     * 
     * @return true if available, false otherwise
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Interface for accessing health data from local storage.
 * This is typically used for caching data from the remote source.
 */
interface LocalHealthDataSource : HealthDataSource {
    /**
     * Stores sleep sessions in the local cache.
     * 
     * @param sessions The sessions to store
     */
    suspend fun storeSleepSessions(sessions: List<SleepSession>)
    
    /**
     * Retrieves cached sleep sessions within the specified time range.
     * 
     * @param start The start time of the range
     * @param end The end time of the range
     * @return List of sleep sessions within the range
     */
    suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession>
    
    /**
     * Stores average wake-up time for a specific time zone.
     * 
     * @param timeZone The time zone
     * @param wakeUpTime The average wake-up time
     */
    suspend fun storeAverageWakeUpTime(timeZone: TimeZone, wakeUpTime: TimeOfDay)
    
    /**
     * Retrieves the cached average wake-up time for a specific time zone.
     * 
     * @param timeZone The time zone
     * @return The average wake-up time, or null if not cached
     */
    suspend fun getAverageWakeUpTime(timeZone: TimeZone): TimeOfDay?
    
    /**
     * Stores average sleep time for a specific time zone.
     * 
     * @param timeZone The time zone
     * @param sleepTime The average sleep time
     */
    suspend fun storeAverageSleepTime(timeZone: TimeZone, sleepTime: TimeOfDay)
    
    /**
     * Retrieves the cached average sleep time for a specific time zone.
     * 
     * @param timeZone The time zone
     * @return The average sleep time, or null if not cached
     */
    suspend fun getAverageSleepTime(timeZone: TimeZone): TimeOfDay?
    
    /**
     * Clears all cached health data.
     */
    suspend fun clearCache()
}

/**
 * Interface for accessing health data from platform-specific APIs.
 * This includes Android's Health Connect, iOS HealthKit, etc.
 */
interface RemoteHealthDataSource : HealthDataSource {
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
     * Retrieves sleep sessions within the specified time range from the platform API.
     * 
     * @param start The start time of the range
     * @param end The end time of the range
     * @return List of sleep sessions within the range
     */
    suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession>
    
    /**
     * Calculates the average wake-up time from sleep data.
     * 
     * @param timeZone The time zone to use for calculations
     * @param days Number of days to look back for data
     * @return The average wake-up time, or null if insufficient data
     */
    suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
    
    /**
     * Calculates the average sleep time from sleep data.
     * 
     * @param timeZone The time zone to use for calculations
     * @param days Number of days to look back for data
     * @return The average sleep time, or null if insufficient data
     */
    suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int = 30): TimeOfDay?
}