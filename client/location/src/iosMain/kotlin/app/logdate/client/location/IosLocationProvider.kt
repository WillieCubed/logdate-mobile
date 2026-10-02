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
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * iOS [ClientLocationProvider] backed by `CLLocationManager`.
 *
 * Issues one-shot `requestLocation()` calls instead of a continuous subscription — the streaming
 * tracker (see `IosDeviceLocationTracker`) handles long-running updates while this provider exists
 * to answer "where am I right now?" lookups from feature code that does not own a tracker.
 *
 * The provider keeps the most recent fix so a [getCurrentLocation] call within a couple of
 * minutes answers instantly rather than re-prompting CoreLocation.
 */
class IosLocationProvider(
    private val permissionManager: PermissionManager,
) : ClientLocationProvider {
    private val locationManager = CLLocationManager()
    private val _currentLocation = MutableSharedFlow<Location>(replay = 1, extraBufferCapacity = 1)

    override val currentLocation: SharedFlow<Location> = _currentLocation.asSharedFlow()

    private val pendingRequests = mutableListOf<CancellableContinuation<LocationFix>>()

    private var latestFix: LocationFix? = null

    private val locationDelegate =
        object :
            NSObject(),
            CLLocationManagerDelegateProtocol {
            override fun locationManager(
                manager: CLLocationManager,
                didUpdateLocations: List<*>,
            ) {
                // CoreLocation marks a fix whose coordinate is invalid with a negative accuracy.
                val mostRecent = didUpdateLocations.filterIsInstance<CLLocation>().lastOrNull { it.horizontalAccuracy >= 0 }
                if (mostRecent == null) {
                    drainPending(success = null, failure = null)
                    return
                }
                val fix = mostRecent.toFix()
                latestFix = fix
                _currentLocation.tryEmit(fix.location)
                drainPending(success = fix, failure = null)
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

    /** Answers instantly from the last fix when there is one, so callers such as note saving never wait on CoreLocation. */
    override suspend fun getCurrentLocation(): Location {
        if (!hasLocationPermission()) {
            error("Location permission not granted")
        }
        return latestFix?.location ?: requestFix().location
    }

    /**
     * Answers from the last fix while it is recent; an older one would place the person where they
     * used to be. If CoreLocation does not answer in time, the older fix is returned with its real
     * age rather than holding the caller up.
     */
    override suspend fun getCurrentFix(): LocationFix {
        if (!hasLocationPermission()) {
            error("Location permission not granted")
        }
        val cached = latestFix
        if (cached != null && Clock.System.now() - cached.observedAt <= MAXIMUM_CACHED_FIX_AGE) return cached
        return withTimeoutOrNull(ONE_SHOT_TIMEOUT) { requestOneShot() } ?: cached ?: error("No location fix within $ONE_SHOT_TIMEOUT")
    }

    private suspend fun requestFix(): LocationFix =
        withTimeoutOrNull(ONE_SHOT_TIMEOUT) { requestOneShot() } ?: error("No location fix within $ONE_SHOT_TIMEOUT")

    override suspend fun refreshLocation() {
        if (!hasLocationPermission()) {
            Napier.d("refreshLocation: permission not granted, skipping")
            return
        }
        runCatching { requestOneShot() }
            .onFailure { Napier.w("refreshLocation: $it") }
    }

    private suspend fun requestOneShot(): LocationFix =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                pendingRequests += continuation
                continuation.invokeOnCancellation { pendingRequests.remove(continuation) }
                locationManager.requestLocation()
            }
        }

    private fun drainPending(
        success: LocationFix?,
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

    private fun CLLocation.toFix(): LocationFix =
        LocationFix(
            location =
                coordinate.useContents {
                    Location(
                        latitude = latitude,
                        longitude = longitude,
                        altitude = LocationAltitude(this@toFix.altitude, AltitudeUnit.METERS),
                    )
                },
            observedAt = Instant.fromEpochMilliseconds((timestamp.timeIntervalSince1970 * 1000).toLong()),
            accuracyMeters = horizontalAccuracy.toFloat(),
            speedMetersPerSecond = speed.takeIf { it >= 0 }?.toFloat(),
            bearingDegrees = course.takeIf { it >= 0 }?.toFloat(),
        )

    private companion object {
        val MAXIMUM_CACHED_FIX_AGE = 2.minutes
        val ONE_SHOT_TIMEOUT = 15.seconds
    }
}
