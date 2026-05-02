@file:OptIn(ExperimentalForeignApi::class)

package app.logdate.client.location

import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS [ClientLocationProvider] backed by `CLLocationManager`.
 *
 * Issues one-shot `requestLocation()` calls instead of a continuous subscription — the streaming
 * tracker (see `IosDeviceLocationTracker`) handles long-running updates while this provider exists
 * to answer "where am I right now?" lookups from feature code that does not own a tracker.
 *
 * The provider keeps the most recent fix in [currentLocation]'s replay cache so the next
 * [getCurrentLocation] call answers instantly rather than re-prompting CoreLocation.
 */
class IosLocationProvider(
    private val permissionManager: PermissionManager,
) : ClientLocationProvider {
    private val locationManager = CLLocationManager()
    private val _currentLocation = MutableSharedFlow<Location>(replay = 1, extraBufferCapacity = 1)

    override val currentLocation: SharedFlow<Location> = _currentLocation.asSharedFlow()

    private val pendingRequests = mutableListOf<CancellableContinuation<Location>>()

    private val locationDelegate =
        object :
            NSObject(),
            CLLocationManagerDelegateProtocol {
            override fun locationManager(
                manager: CLLocationManager,
                didUpdateLocations: List<*>,
            ) {
                val mostRecent = didUpdateLocations.filterIsInstance<CLLocation>().lastOrNull() ?: return
                val mapped = mostRecent.toModel()
                _currentLocation.tryEmit(mapped)
                drainPending(success = mapped, failure = null)
            }

            override fun locationManager(
                manager: CLLocationManager,
                didFailWithError: NSError,
            ) {
                Napier.w("CLLocationManager failed: ${didFailWithError.localizedDescription}")
                drainPending(success = null, failure = didFailWithError)
            }
        }

    init {
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        locationManager.delegate = locationDelegate
    }

    override fun hasLocationPermission(): Boolean =
        permissionManager.isPermissionGranted(PermissionType.LOCATION) ||
            isAuthorizedFromCoreLocation()

    override suspend fun getCurrentLocation(): Location {
        if (!hasLocationPermission()) {
            error("Location permission not granted")
        }
        _currentLocation.replayCache.firstOrNull()?.let { return it }
        return requestOneShot()
    }

    override suspend fun refreshLocation() {
        if (!hasLocationPermission()) {
            Napier.d("refreshLocation: permission not granted, skipping")
            return
        }
        runCatching { requestOneShot() }
            .onFailure { Napier.w("refreshLocation: $it") }
    }

    private suspend fun requestOneShot(): Location =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                pendingRequests += continuation
                continuation.invokeOnCancellation { pendingRequests.remove(continuation) }
                locationManager.requestLocation()
            }
        }

    private fun drainPending(
        success: Location?,
        failure: NSError?,
    ) {
        if (pendingRequests.isEmpty()) return
        val snapshot = pendingRequests.toList()
        pendingRequests.clear()
        snapshot.forEach { continuation ->
            if (!continuation.isActive) return@forEach
            if (success != null) {
                continuation.resume(success)
            } else {
                val message = failure?.localizedDescription ?: "CLLocationManager error"
                continuation.resumeWithException(IllegalStateException(message))
            }
        }
    }

    private fun isAuthorizedFromCoreLocation(): Boolean {
        val status = locationManager.authorizationStatus
        return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
    }

    private fun CLLocation.toModel(): Location =
        coordinate.useContents {
            Location(
                latitude = latitude,
                longitude = longitude,
                altitude = LocationAltitude(this@toModel.altitude, AltitudeUnit.METERS),
            )
        }
}
