package app.logdate.client.location.places

import app.logdate.client.location.ClientLocationProvider
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.coroutines.suspendCoroutine

object StubLocationProvider : ClientLocationProvider {
    private val _currentLocation =
        MutableStateFlow(Location(0.0, 0.0, LocationAltitude(0.0, AltitudeUnit.FEET)))
    override val currentLocation: SharedFlow<Location>
        get() = _currentLocation.asSharedFlow()

    override suspend fun getCurrentLocation(): Location = suspendCoroutine {
        _currentLocation
    }

    override suspend fun refreshLocation() {
        // TODO: Implement location refresh
    }
}