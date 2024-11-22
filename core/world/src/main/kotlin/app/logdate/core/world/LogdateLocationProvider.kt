package app.logdate.core.world

import android.location.Location
import kotlinx.coroutines.flow.Flow

@Deprecated(
    "Use LogdateLocationProvider instead. It's meant to be multiplatform.",
    replaceWith = ReplaceWith(
        "LogdateLocationProvider",
        "app.logdate.core.world.LogdateLocationProvider"
    )
)
interface LogdateLocationProvider {
    @Throws(SecurityException::class)
    fun getCurrentLocation(): Location

    @Throws(SecurityException::class)
    fun observeLocation(): Flow<Location>
}
