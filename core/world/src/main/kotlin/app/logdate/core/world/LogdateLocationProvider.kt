package app.logdate.core.world

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LogdateLocationProvider {
    fun getCurrentLocation(): Location
    fun observeLocation(): Flow<Location>
}
