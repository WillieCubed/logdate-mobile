package app.logdate.client.location

import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Stub implementation of ClientLocationProvider for iOS.
 * 
 * This is a temporary implementation to allow compilation.
 * It should be replaced with a real implementation using CoreLocation.
 */
class IosLocationProvider : ClientLocationProvider {
    
    private val _currentLocation = MutableSharedFlow<Location>(replay = 1)
    
    override val currentLocation: SharedFlow<Location> = _currentLocation.asSharedFlow()
    
    override suspend fun getCurrentLocation(): Location {
        // Return a default location
        return Location(
            latitude = 0.0,
            longitude = 0.0,
            altitude = LocationAltitude(0.0, AltitudeUnit.METERS)
        )
    }
    
    override suspend fun refreshLocation() {
        // Stub implementation - does nothing
    }
    
    /**
     * Check if the app has location permission
     * @return true if the app has location permission, false otherwise
     */
    fun hasLocationPermission(): Boolean {
        // Stub implementation - always return true for now
        return true
    }
}