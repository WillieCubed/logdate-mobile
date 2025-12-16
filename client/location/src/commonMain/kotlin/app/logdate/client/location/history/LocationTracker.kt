package app.logdate.client.location.history

import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.shared.model.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Interface for user location tracking and history management.
 * 
 * This interface provides methods for tracking, logging, retrieving, 
 * and managing the user's location history across time.
 */
interface LocationTracker {
    
    /**
     * Gets the most recent location entry.
     */
    suspend fun getLastLocation(): LocationHistoryItem?
    
    /**
     * Observes the most recent location as a flow.
     */
    fun observeCurrentLocation(): Flow<LocationHistoryItem?>
    
    /**
     * Gets all location history entries for a specific date.
     */
    suspend fun getLocationHistoryForDate(date: LocalDate): List<LocationHistoryItem>
    
    /**
     * Gets all location history entries between two timestamps.
     */
    suspend fun getLocationHistoryBetween(start: Instant, end: Instant): List<LocationHistoryItem>
    
    /**
     * Logs the current location from the location provider.
     */
    suspend fun logCurrentLocation(): Result<LocationHistoryItem>
    
    /**
     * Logs a specific location with optional metadata.
     */
    suspend fun logLocation(
        location: Location,
        timestamp: Instant = Clock.System.now(),
        metadata: Map<String, Any> = emptyMap()
    ): Result<LocationHistoryItem>
    
    /**
     * Deletes a specific location history entry.
     */
    suspend fun deleteLocationEntry(historyItem: LocationHistoryItem): Result<Unit>
    
    /**
     * Clears location history between two timestamps.
     */
    suspend fun clearLocationHistory(start: Instant, end: Instant): Result<Unit>
    
    /**
     * Clears all location history.
     */
    suspend fun clearAllLocationHistory(): Result<Unit>
}