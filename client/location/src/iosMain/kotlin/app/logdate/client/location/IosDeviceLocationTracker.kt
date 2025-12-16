package app.logdate.client.location

import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.darwin.NSObject

/**
 * iOS-specific implementation of DeviceLocationTracker.
 * 
 * Uses CoreLocation for efficient location tracking.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDeviceLocationTracker(
    private val locationProvider: IosLocationProvider
) : DeviceLocationTracker {

    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _locationUpdates = MutableStateFlow<Location?>(null)
    
    private val locationManager = CLLocationManager()
    private val locationDelegate = LocationManagerDelegate()
    
    private var isTrackingActive = false
    
    init {
        locationManager.delegate = locationDelegate
    }
    
    override suspend fun getCurrentLocation(): Location {
        return locationProvider.getCurrentLocation()
    }
    
    override fun observeLocationUpdates(): Flow<Location> {
        return _locationUpdates.asStateFlow() as Flow<Location>
    }
    
    override fun isTrackingEnabled(): Boolean = isTrackingActive
    
    override suspend fun startTracking(): Boolean {
        if (!locationProvider.hasLocationPermission()) {
            Napier.w("Location tracking not started: Missing location permission")
            return false
        }
        
        if (isTrackingActive) {
            Napier.d("Location tracking already active")
            return true
        }
        
        return try {
            configureLocationManager()
            locationManager.startUpdatingLocation()
            isTrackingActive = true
            Napier.i("Location tracking started successfully")
            true
        } catch (e: Exception) {
            Napier.e("Failed to start location tracking", e)
            false
        }
    }
    
    override suspend fun stopTracking() {
        locationManager.stopUpdatingLocation()
        isTrackingActive = false
        Napier.i("Location tracking stopped")
    }
    
    override fun release() {
        locationManager.stopUpdatingLocation()
        isTrackingActive = false
        trackerScope.cancel()
        Napier.d("Released iOS location tracker resources")
    }
    
    private fun configureLocationManager() {
        // Configure for balanced accuracy
        locationManager.desiredAccuracy = platform.CoreLocation.kCLLocationAccuracyHundredMeters
        
        // Set distance filter (minimum distance in meters device must move for update)
        locationManager.distanceFilter = 10.0
        
        // Configure background updates if supported
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.showsBackgroundLocationIndicator = true
    }
    
    /**
     * Delegate to handle location updates from CoreLocation.
     */
    @OptIn(ExperimentalForeignApi::class)
    private inner class LocationManagerDelegate : NSObject(), CLLocationManagerDelegateProtocol {
        
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val locations = didUpdateLocations.filterIsInstance<CLLocation>()
            if (locations.isEmpty()) return
            
            // Process most recent location
            val clLocation = locations.last()
            
            // Access coordinate struct fields safely
            val coordinate = clLocation.coordinate.useContents { 
                // Create a simple data class to hold coordinate values
                data class Coord(val lat: Double, val lon: Double)
                Coord(latitude, longitude)
            }
            
            val logDateLocation = Location(
                latitude = coordinate.lat,
                longitude = coordinate.lon,
                altitude = LocationAltitude(
                    value = clLocation.altitude,
                    units = AltitudeUnit.METERS
                )
            )
            
            _locationUpdates.value = logDateLocation
        }
        
        override fun locationManager(manager: CLLocationManager, didFailWithError: platform.Foundation.NSError) {
            Napier.e("Location manager error: ${didFailWithError.localizedDescription}")
        }
    }
}