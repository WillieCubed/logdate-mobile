@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.logdate.client.location.places

import app.logdate.shared.model.Location
import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocationCoordinate2D
import platform.MapKit.MKLocalPointsOfInterestRequest
import platform.MapKit.MKLocalSearch
import platform.MapKit.MKLocalSearchResponse
import platform.MapKit.MKMapItem
import kotlin.coroutines.resume

private const val POI_RADIUS_METERS = 800.0

class IosExternalPlacesProvider : ExternalPlacesProvider {
    override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> {
        val center =
            cValue<CLLocationCoordinate2D> {
                latitude = location.latitude
                longitude = location.longitude
            }
        val request = MKLocalPointsOfInterestRequest(centerCoordinate = center, radius = POI_RADIUS_METERS)
        val search = MKLocalSearch(pointsOfInterestRequest = request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { search.cancel() }
            search.startWithCompletionHandler { response: MKLocalSearchResponse?, error ->
                if (!continuation.isActive) return@startWithCompletionHandler
                if (error != null) {
                    Napier.w("MKLocalSearch failed: ${error.localizedDescription}")
                    continuation.resume(emptyList())
                    return@startWithCompletionHandler
                }
                @Suppress("UNCHECKED_CAST")
                val items = (response?.mapItems as? List<MKMapItem>).orEmpty()
                continuation.resume(items.mapNotNull { it.toSuggestion() })
            }
        }
    }

    private fun MKMapItem.toSuggestion(): PlaceSuggestion? {
        val placemark = this.placemark
        val coords =
            placemark.coordinate.useContents {
                latitude to longitude
            }
        val name = name ?: placemark.name ?: return null
        val addressComponents =
            listOfNotNull(
                placemark.thoroughfare,
                placemark.locality,
                placemark.administrativeArea,
            )
        return PlaceSuggestion(
            name = name,
            address = addressComponents.joinToString(", "),
            latitude = coords.first,
            longitude = coords.second,
            confidence = 80,
            category = pointOfInterestCategory,
            externalId = null,
        )
    }
}
