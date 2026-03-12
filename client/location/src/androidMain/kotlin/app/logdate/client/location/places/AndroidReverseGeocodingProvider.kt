package app.logdate.client.location.places

import android.location.Address
import android.location.Geocoder
import android.os.Build
import app.logdate.shared.model.Location
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidReverseGeocodingProvider(
    private val geocoder: Geocoder,
) : ReverseGeocodingProvider {
    override suspend fun reverseGeocode(location: Location): GeocodedAddress? =
        try {
            val address =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+: async API
                    suspendCancellableCoroutine<Address?> { continuation ->
                        geocoder.getFromLocation(
                            location.latitude,
                            location.longitude,
                            1,
                        ) { addresses ->
                            continuation.resume(addresses.firstOrNull())
                        }
                    }
                } else {
                    // API < 33: blocking API
                    @Suppress("DEPRECATION")
                    withContext(Dispatchers.IO) {
                        geocoder
                            .getFromLocation(
                                location.latitude,
                                location.longitude,
                                1,
                            )?.firstOrNull()
                    }
                }

            address?.let {
                GeocodedAddress(
                    thoroughfare = it.thoroughfare,
                    subLocality = it.subLocality,
                    locality = it.locality,
                    adminArea = it.adminArea,
                )
            }
        } catch (e: Exception) {
            Napier.w(
                "Reverse geocoding failed for ${location.latitude}, ${location.longitude}",
                e,
            )
            null
        }
}
