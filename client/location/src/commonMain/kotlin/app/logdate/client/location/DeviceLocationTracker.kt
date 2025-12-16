package app.logdate.client.location

import app.logdate.shared.model.Location
import kotlinx.coroutines.flow.Flow

/**
 * Interface for tracking the device's current location.
 * 
 * This interface focuses solely on obtaining and observing the current physical
 * location of the device, without any history management functionality.
 */
interface DeviceLocationTracker {
    
    /**
     * Gets the current location of the device.
     * 
     * @return The current location
     * @throws Exception if location cannot be determined
     */
    suspend fun getCurrentLocation(): Location
    
    /**
     * Observes the device's location changes as a flow.
     * 
     * @return A flow that emits the current location whenever it changes
     */
    fun observeLocationUpdates(): Flow<Location>
    
    /**
     * Checks if location tracking is currently enabled.
     * 
     * @return true if location tracking is enabled, false otherwise
     */
    fun isTrackingEnabled(): Boolean
    
    /**
     * Starts location tracking if not already active.
     * 
     * @return true if tracking was successfully started or was already active
     */
    suspend fun startTracking(): Boolean
    
    /**
     * Stops location tracking.
     */
    suspend fun stopTracking()
    
    /**
     * Releases any resources used by the location tracker.
     * Should be called when the tracker is no longer needed.
     */
    fun release()
}