@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.logdate.client.location.places

import app.logdate.shared.model.Location
import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import kotlin.coroutines.resume

class IosReverseGeocodingProvider : ReverseGeocodingProvider {
    private val geocoder = CLGeocoder()

    override suspend fun reverseGeocode(location: Location): GeocodedAddress? {
        val clLocation = CLLocation(latitude = location.latitude, longitude = location.longitude)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { geocoder.cancelGeocode() }
            geocoder.reverseGeocodeLocation(clLocation) { placemarks, error ->
                if (!continuation.isActive) return@reverseGeocodeLocation
                if (error != null) {
                    Napier.w("CLGeocoder reverseGeocodeLocation failed: ${error.localizedDescription}")
                    continuation.resume(null)
                    return@reverseGeocodeLocation
                }
                @Suppress("UNCHECKED_CAST")
                val first = (placemarks as? List<CLPlacemark>)?.firstOrNull()
                continuation.resume(first?.toGeocodedAddress())
            }
        }
    }

    private fun CLPlacemark.toGeocodedAddress(): GeocodedAddress =
        GeocodedAddress(
            thoroughfare = thoroughfare,
            subLocality = subLocality,
            locality = locality,
            adminArea = administrativeArea,
        )
}
