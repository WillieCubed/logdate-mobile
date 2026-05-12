package app.logdate.client.location.places

import app.logdate.shared.model.Location

/**
 * Converts geographic coordinates into human-readable addresses.
 */
interface ReverseGeocodingProvider {
    /**
     * Returns a [GeocodedAddress] for the given [location], or `null` if geocoding is
     * unavailable or the lookup fails.
     */
    suspend fun reverseGeocode(location: Location): GeocodedAddress?
}

/**
 * A human-readable address resolved from geographic coordinates.
 *
 * @property thoroughfare Street name (e.g. "Main St").
 * @property subLocality Neighborhood or district.
 * @property locality City or town.
 * @property adminArea State or province.
 */
data class GeocodedAddress(
    val thoroughfare: String?,
    val subLocality: String?,
    val locality: String?,
    val adminArea: String?,
)

/**
 * No-op [ReverseGeocodingProvider] for platforms that do not support geocoding.
 *
 * @see AndroidReverseGeocodingProvider
 */
class UnavailableReverseGeocodingProvider : ReverseGeocodingProvider {
    override suspend fun reverseGeocode(location: Location): GeocodedAddress? = null
}
