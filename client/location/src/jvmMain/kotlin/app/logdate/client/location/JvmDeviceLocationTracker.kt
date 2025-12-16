package app.logdate.client.location

import app.logdate.shared.model.Location
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop-specific implementation of DeviceLocationTracker.
 * 
 * Desktop platforms generally have limited location capabilities,
 * so this implementation primarily works with the DesktopLocationProvider
 * which may provide simulated location data.
 */
class JvmDeviceLocationTracker(
    private val locationProvider: DesktopLocationProvider
) : DeviceLocationTracker {

    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _locationUpdates = MutableStateFlow<Location?>(null)
    private val isTrackingActive = AtomicBoolean(false)
    private val updateIntervalMs = 30_000L // 30 seconds
    
    override suspend fun getCurrentLocation(): Location {
        return locationProvider.getCurrentLocation()
    }
    
    override fun observeLocationUpdates(): Flow<Location> {
        return _locationUpdates
            .asStateFlow()
            .mapNotNull { it } // Filter out null values and convert to non-nullable Flow
    }
    
    override fun isTrackingEnabled(): Boolean {
        return isTrackingActive.get()
    }
    
    override suspend fun startTracking(): Boolean {
        if (isTrackingActive.getAndSet(true)) {
            // Already tracking
            return true
        }
        
        startLocationPolling()
        Napier.i("Started location tracking on desktop")
        return true
    }
    
    override suspend fun stopTracking() {
        isTrackingActive.set(false)
        Napier.i("Stopped location tracking on desktop")
    }
    
    override fun release() {
        isTrackingActive.set(false)
        trackerScope.cancel()
        Napier.d("Released desktop location tracker resources")
    }
    
    private fun startLocationPolling() {
        trackerScope.launch {
            while (isActive && isTrackingActive.get()) {
                try {
                    val location = locationProvider.getCurrentLocation()
                    _locationUpdates.value = location
                } catch (e: Exception) {
                    Napier.e("Failed to update location", e)
                }
                
                delay(updateIntervalMs)
            }
        }
    }
}