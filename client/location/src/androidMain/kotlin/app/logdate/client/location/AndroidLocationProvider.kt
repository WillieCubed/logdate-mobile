package app.logdate.client.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location as AndroidLocation
import android.os.Looper
import androidx.core.content.ContextCompat
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android implementation of location provider using Google Play Services.
 * 
 * Provides real-time location updates for personal location logging with
 * configurable accuracy and update intervals.
 */
class AndroidLocationProvider(
    private val context: Context
) : ClientLocationProvider {
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    private val _currentLocation = MutableSharedFlow<Location>(replay = 1)
    override val currentLocation: SharedFlow<Location> = _currentLocation.asSharedFlow()
    
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
        .setWaitForAccurateLocation(false)
        .setMinUpdateIntervalMillis(10_000L)
        .setMaxUpdateDelayMillis(60_000L)
        .build()
    
    private var locationCallback: LocationCallback? = null
    private var isLocationUpdatesActive = false
    
    init {
        startLocationUpdates()
        // Try to emit last known location immediately if available
        tryEmitLastKnownLocation()
    }
    
    override suspend fun getCurrentLocation(): Location {
        if (!hasLocationPermission()) {
            throw SecurityException("Location permission not granted. Please enable location access in your device settings.")
        }
        
        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
            
            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { androidLocation ->
                    if (androidLocation != null) {
                        val location = androidLocation.toLogDateLocation()
                        _currentLocation.tryEmit(location)
                        continuation.resume(location)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException("Unable to get current location")
                        )
                    }
                }.addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }
    }
    
    override suspend fun refreshLocation() {
        if (hasLocationPermission()) {
            getCurrentLocation()
        }
    }
    
    /**
     * Starts continuous location updates for personal location logging.
     */
    private fun startLocationUpdates() {
        if (!hasLocationPermission() || isLocationUpdatesActive) return
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { androidLocation ->
                    val location = androidLocation.toLogDateLocation()
                    _currentLocation.tryEmit(location)
                }
            }
            
            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    // Handle location unavailable state
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isLocationUpdatesActive = true
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
    
    /**
     * Stops location updates to preserve battery.
     */
    fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            isLocationUpdatesActive = false
        }
    }
    
    /**
     * Provides a flow of continuous location updates for monitoring.
     */
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }
        
        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { androidLocation ->
                    val location = androidLocation.toLogDateLocation()
                    trySend(location)
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }
        
        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
    
    /**
     * Checks if location permissions are granted.
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun AndroidLocation.toLogDateLocation(): Location {
        return Location(
            latitude = latitude,
            longitude = longitude,
            altitude = LocationAltitude(
                value = if (hasAltitude()) altitude else 0.0,
                units = AltitudeUnit.METERS
            )
        )
    }
    
    /**
     * Tries to emit the last known location immediately if available and permissions are granted.
     * This helps provide immediate location data for the UI.
     */
    private fun tryEmitLastKnownLocation() {
        if (!hasLocationPermission()) return
        
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { androidLocation ->
                androidLocation?.let { location ->
                    val logDateLocation = location.toLogDateLocation()
                    _currentLocation.tryEmit(logDateLocation)
                }
            }.addOnFailureListener {
                // Last known location not available, that's okay
            }
        } catch (e: SecurityException) {
            // Permission not available, that's okay
        }
    }
}