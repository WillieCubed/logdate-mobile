package app.logdate.client.location.history

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class StandardLocationTrackerCancellationTest {
    private val location = Location(36.0, -115.0, LocationAltitude(0.0, AltitudeUnit.METERS))

    private val cancelledRepository =
        Proxy.newProxyInstance(
            LocationHistoryRepository::class.java.classLoader,
            arrayOf(LocationHistoryRepository::class.java),
        ) { _, _, _ -> throw CancellationException("caller left") } as LocationHistoryRepository

    private fun provider(cancelFix: Boolean) =
        object : ClientLocationProvider {
            override val currentLocation = MutableSharedFlow<Location>()

            override fun hasLocationPermission() = true

            override suspend fun getCurrentLocation(): Location {
                if (cancelFix) throw CancellationException("caller left")
                return location
            }

            override suspend fun refreshLocation() = Unit
        }

    @Test
    fun `a cancelled current fix is not reported as a failed log`() =
        runTest {
            val tracker = StandardLocationTracker(provider(cancelFix = true), cancelledRepository, "device")

            assertFailsWith<CancellationException> { tracker.logCurrentLocation() }
        }

    @Test
    fun `a cancelled history write is not reported as a failed log`() =
        runTest {
            val tracker = StandardLocationTracker(provider(cancelFix = false), cancelledRepository, "device")

            assertFailsWith<CancellationException> { tracker.logLocation(location, Instant.fromEpochMilliseconds(1000)) }
        }

    @Test
    fun `a cancelled history clear is not reported as a failed clear`() =
        runTest {
            val tracker = StandardLocationTracker(provider(cancelFix = false), cancelledRepository, "device")

            assertFailsWith<CancellationException> { tracker.clearAllLocationHistory() }
        }
}
