package app.logdate.client.repository.location

import app.logdate.shared.model.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Repository for managing location history and logging.
 */
interface LocationHistoryRepository {
    
    /**
     * Gets all location history, ordered by most recent first.
     */
    suspend fun getAllLocationHistory(): List<LocationHistoryItem>
    
    /**
     * Observes all location history.
     */
    fun observeLocationHistory(): Flow<List<LocationHistoryItem>>
    
    /**
     * Gets recent location history with a limit.
     */
    suspend fun getRecentLocationHistory(limit: Int = 50): List<LocationHistoryItem>
    
    /**
     * Gets location history between two timestamps.
     */
    suspend fun getLocationHistoryBetween(
        startTime: Instant, 
        endTime: Instant
    ): List<LocationHistoryItem>
    
    /**
     * Gets the most recent location.
     */
    suspend fun getLastLocation(): LocationHistoryItem?
    
    /**
     * Observes the most recent location.
     */
    fun observeLastLocation(): Flow<LocationHistoryItem?>
    
    /**
     * Logs a new location entry.
     */
    suspend fun logLocation(
        location: Location,
        userId: String,
        deviceId: String,
        confidence: Float = 1.0f,
        isGenuine: Boolean = true
    ): Result<Unit>
    
    /**
     * Deletes a specific location entry.
     */
    suspend fun deleteLocationEntry(
        userId: String,
        deviceId: String,
        timestamp: Instant
    ): Result<Unit>
    
    /**
     * Deletes location entries within a time range.
     */
    suspend fun deleteLocationsBetween(
        startTime: Instant,
        endTime: Instant
    ): Result<Unit>
    
    /**
     * Gets total count of location entries.
     */
    suspend fun getLocationCount(): Int
}

/**
 * Domain model for location history item.
 */
data class LocationHistoryItem(
    val userId: String,
    val deviceId: String,
    val timestamp: Instant,
    val location: Location,
    val confidence: Float,
    val isGenuine: Boolean
)