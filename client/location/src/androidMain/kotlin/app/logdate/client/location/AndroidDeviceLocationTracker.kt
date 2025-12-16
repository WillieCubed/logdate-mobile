package app.logdate.client.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import app.logdate.shared.model.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.tasks.await

/**
 * Android-specific implementation of DeviceLocationTracker.
 * 
 * Uses Google Play Services FusedLocationProvider for efficient location tracking.
 */
class AndroidDeviceLocationTracker(
    private val context: Context,
    private val locationProvider: AndroidLocationProvider
) : DeviceLocationTracker {

    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _locationUpdates = MutableStateFlow<Location?>(null)
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    private var locationCallback: LocationCallback? = null
    private var isTrackingActive = false
    
    override suspend fun getCurrentLocation(): Location {
        return locationProvider.getCurrentLocation()
    }
    
    override fun observeLocationUpdates(): Flow<Location> {
        return _locationUpdates
            .asStateFlow()
            .filterNotNull()
    }
    
    override fun isTrackingEnabled(): Boolean = isTrackingActive
    
    override suspend fun startTracking(): Boolean {
        if (!hasLocationPermission()) {
            Napier.w("Location tracking not started: Missing location permission")
            return false
        }
        
        if (isTrackingActive) {
            Napier.d("Location tracking already active")
            return true
        }
        
        return try {
            setupLocationTracking()
            isTrackingActive = true
            Napier.i("Location tracking started successfully")
            true
        } catch (e: Exception) {
            Napier.e("Failed to start location tracking", e)
            false
        }
    }
    
    override suspend fun stopTracking() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
        
        isTrackingActive = false
        Napier.i("Location tracking stopped")
    }
    
    override fun release() {
        if (isTrackingActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback ?: return)
            locationCallback = null
            isTrackingActive = false
        }
        trackerScope.cancel()
        Napier.d("Released Android location tracker resources")
    }
    
    private fun setupLocationTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000)
            .setMinUpdateDistanceMeters(10f)
            .setMaxUpdateDelayMillis(60000)
            .build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val androidLocation = result.lastLocation ?: return
                
                val logDateLocation = Location(
                    latitude = androidLocation.latitude,
                    longitude = androidLocation.longitude,
                    altitude = app.logdate.shared.model.LocationAltitude(
                        value = if (androidLocation.hasAltitude()) androidLocation.altitude else 0.0,
                        units = app.logdate.shared.model.AltitudeUnit.METERS
                    )
                )
                
                _locationUpdates.value = logDateLocation
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            throw SecurityException("Location permission not granted", e)
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}