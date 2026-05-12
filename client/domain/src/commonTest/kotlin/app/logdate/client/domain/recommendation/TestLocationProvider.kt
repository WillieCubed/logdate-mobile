package app.logdate.client.domain.recommendation

import app.logdate.client.location.ClientLocationProvider
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TestLocationProvider : ClientLocationProvider {
    private val location =
        Location(
            latitude = 37.3349,
            longitude = -122.0090,
            altitude = LocationAltitude(100.0, AltitudeUnit.FEET),
        )

    private val locationFlow = MutableStateFlow(location)

    override val currentLocation: SharedFlow<Location> = locationFlow.asSharedFlow()

    override fun hasLocationPermission(): Boolean = true

    override suspend fun getCurrentLocation(): Location = location

    override suspend fun refreshLocation() {
        locationFlow.value = location
    }
}
