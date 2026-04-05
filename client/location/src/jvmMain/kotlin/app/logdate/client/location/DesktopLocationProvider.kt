package app.logdate.client.location

import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Desktop implementation of ClientLocationProvider.
 *
 * Stub implementation that returns a fixed location for desktop platforms
 * where GPS is typically not available.
 */
class DesktopLocationProvider(
    private val permissionManager: app.logdate.client.permissions.PermissionManager,
) : ClientLocationProvider {
    private val locationFlowState = MutableSharedFlow<Location>(replay = 1)
    override val currentLocation: SharedFlow<Location> = locationFlowState.asSharedFlow()

    // San Francisco default location
    private val defaultLocation =
        Location(
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = LocationAltitude(52.0, AltitudeUnit.METERS),
        )

    init {
        // Emit the default location immediately
        try {
            locationFlowState.tryEmit(defaultLocation)
        } catch (e: Exception) {
            Napier.w("Failed to emit default location: ${e.message}")
        }
    }

    override fun hasLocationPermission(): Boolean =
        permissionManager.isPermissionGranted(app.logdate.client.permissions.PermissionType.LOCATION)

    override suspend fun getCurrentLocation(): Location = defaultLocation

    override suspend fun refreshLocation() {
        Napier.d("Refreshing location on desktop (simulated)")
        // Simulate a brief delay for realism
        delay(500)
        locationFlowState.emit(defaultLocation)
    }
}
