package app.logdate.core.world

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LogdateLocationProvider {
    @Throws(SecurityException::class)
    fun getCurrentLocation(): Location

    @Throws(SecurityException::class)
    fun observeLocation(): Flow<Location>
}
