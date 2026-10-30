package app.logdate.client.data.location

import app.logdate.client.database.dao.LocationHistoryDao
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class LocationHistoryCancellationTest {
    private val cancelledDao =
        Proxy.newProxyInstance(
            LocationHistoryDao::class.java.classLoader,
            arrayOf(LocationHistoryDao::class.java),
        ) { _, _, _ -> throw CancellationException("caller left") } as LocationHistoryDao

    private val repository = OfflineFirstLocationHistoryRepository(cancelledDao)

    @Test
    fun `a cancelled location write is not reported as a failed write`() =
        runTest {
            val record =
                LocationLogRecord(
                    userId = "owner",
                    deviceId = "device",
                    timestamp = Instant.fromEpochMilliseconds(1000),
                    loggedAt = Instant.fromEpochMilliseconds(1000),
                    location = Location(36.0, -115.0, LocationAltitude(0.0, AltitudeUnit.METERS)),
                    confidence = 1f,
                    isGenuine = true,
                )

            assertFailsWith<CancellationException> { repository.logLocation(record) }
        }

    @Test
    fun `a cancelled location delete is not reported as a failed delete`() =
        runTest {
            assertFailsWith<CancellationException> {
                repository.deleteLocationEntry("owner", "device", Instant.fromEpochMilliseconds(1000))
            }
            assertFailsWith<CancellationException> {
                repository.deleteLocationsBetween(Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(1000))
            }
        }
}
