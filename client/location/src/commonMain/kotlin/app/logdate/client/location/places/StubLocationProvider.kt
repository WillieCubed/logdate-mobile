package app.logdate.client.location.places

import app.logdate.client.location.ClientLocationProvider
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object StubLocationProvider : ClientLocationProvider {
    // Mock location: Apple Park, Cupertino, CA (Apple's headquarters)
    private val mockLocation = Location(
        latitude = 37.3349,
        longitude = -122.0090,
        altitude = LocationAltitude(100.0, AltitudeUnit.FEET)
    )
    
    private val _currentLocation = MutableStateFlow(mockLocation)
    override val currentLocation: SharedFlow<Location>
        get() = _currentLocation.asSharedFlow()

    override suspend fun getCurrentLocation(): Location = mockLocation

    override suspend fun refreshLocation() {
        // Emit the same mock location to simulate a refresh
        _currentLocation.value = mockLocation
    }
}