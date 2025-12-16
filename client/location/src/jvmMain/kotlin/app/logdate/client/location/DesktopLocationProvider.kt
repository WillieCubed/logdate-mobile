package app.logdate.client.location

import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Desktop implementation of ClientLocationProvider.
 * 
 * Stub implementation that returns a fixed location for desktop platforms
 * where GPS is typically not available.
 */
class DesktopLocationProvider : ClientLocationProvider {
    
    private val _locationFlow = MutableSharedFlow<Location>(replay = 1)
    override val currentLocation: SharedFlow<Location> = _locationFlow.asSharedFlow()
    
    // San Francisco default location
    private val defaultLocation = Location(
        latitude = 37.7749,
        longitude = -122.4194,
        altitude = LocationAltitude(52.0, AltitudeUnit.METERS)
    )
    
    init {
        // Emit the default location immediately
        try {
            _locationFlow.tryEmit(defaultLocation)
        } catch (e: Exception) {
            println("Failed to emit default location: ${e.message}")
        }
    }
    
    override suspend fun getCurrentLocation(): Location {
        return defaultLocation
    }
    
    override suspend fun refreshLocation() {
        println("Refreshing location on desktop (simulated)")
        // Simulate a brief delay for realism
        delay(500)
        _locationFlow.emit(defaultLocation)
    }
}