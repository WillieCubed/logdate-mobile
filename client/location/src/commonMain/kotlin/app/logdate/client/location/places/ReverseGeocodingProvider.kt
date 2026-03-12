package app.logdate.client.location.places

import app.logdate.shared.model.Location

interface ReverseGeocodingProvider {
    suspend fun reverseGeocode(location: Location): GeocodedAddress?
}

data class GeocodedAddress(
    val thoroughfare: String?, // street name
    val subLocality: String?, // neighborhood/district
    val locality: String?, // city
    val adminArea: String?, // state/province
)

class StubReverseGeocodingProvider : ReverseGeocodingProvider {
    override suspend fun reverseGeocode(location: Location): GeocodedAddress? = null
}
