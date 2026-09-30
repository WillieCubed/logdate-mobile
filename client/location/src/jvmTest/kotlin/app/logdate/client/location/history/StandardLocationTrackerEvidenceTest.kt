package app.logdate.client.location.history

import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class StandardLocationTrackerEvidenceTest {
    @Test
    fun delayedSamplesKeepCaptureTimeAndMovementEvidence() =
        runTest {
            var saved: LocationLogRecord? = null
            val repository =
                Proxy.newProxyInstance(
                    LocationHistoryRepository::class.java.classLoader,
                    arrayOf(LocationHistoryRepository::class.java),
                ) { _, _, arguments ->
                    saved = arguments.first() as LocationLogRecord
                    Unit
                } as LocationHistoryRepository
            val location = Location(36.0, -115.0, LocationAltitude(0.0, AltitudeUnit.METERS))
            val provider =
                object : ClientLocationProvider {
                    override val currentLocation = MutableSharedFlow<Location>()

                    override fun hasLocationPermission() = true

                    override suspend fun getCurrentLocation() = location

                    override suspend fun refreshLocation() = Unit
                }
            val ownerProvider =
                object : CanonicalOwnerProvider {
                    override suspend fun getCanonicalOwnerId() = "canonical-owner"

                    override suspend fun hasBoundOwner() = true
                }
            val tracker = StandardLocationTracker(provider, repository, "device", canonicalOwnerProvider = ownerProvider)
            val capturedAt = Instant.fromEpochMilliseconds(1000)
            val recordedAt = Instant.fromEpochMilliseconds(2000)
            val result =
                tracker
                    .logLocation(
                        location,
                        capturedAt,
                        mapOf(
                            "loggedAt" to recordedAt,
                            "accuracyMeters" to 8f,
                            "speedMetersPerSecond" to 4f,
                            "bearingDegrees" to 120f,
                            "isMock" to true,
                            "activityType" to "ON_BICYCLE",
                            "timeZoneId" to "America/Los_Angeles",
                        ),
                    ).getOrThrow()
            val record = assertNotNull(saved)
            assertEquals("canonical-owner", record.userId)
            assertEquals("canonical-owner", result.userId)
            assertEquals(capturedAt, record.timestamp)
            assertEquals(recordedAt, record.loggedAt)
            assertEquals(8f, record.accuracyMeters)
            assertEquals(4f, record.speedMetersPerSecond)
            assertEquals(120f, record.bearingDegrees)
            assertEquals(true, record.isMock)
            assertEquals("ON_BICYCLE", record.activityType)
            assertEquals("America/Los_Angeles", record.timeZoneId)
            assertEquals(record.activityType, result.activityType)
            assertEquals(record.timeZoneId, result.timeZoneId)
        }
}
