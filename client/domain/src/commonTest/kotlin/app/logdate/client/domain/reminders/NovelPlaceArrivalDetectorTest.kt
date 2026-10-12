package app.logdate.client.domain.reminders

import app.logdate.client.domain.location.LocationStop
import app.logdate.client.domain.location.LocationStopEvidenceKind
import app.logdate.client.domain.location.LocationStopSnapshot
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.domain.recommendation.DefaultPlaceFamiliarityRepository
import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.location.places.PlaceSuggestion
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class NovelPlaceArrivalDetectorTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val arrived = Instant.parse("2026-10-12T11:50:00Z")
    private val location = Location(37.0, -122.0, LocationAltitude(0.0, AltitudeUnit.METERS))
    private val storage = FakeKeyValueStorage()
    private val familiarity = DefaultPlaceFamiliarityRepository(storage)
    private var latest = listOf(stop())
    private var fix =
        LocationHistoryItem(userId = "user", deviceId = "device", timestamp = now, location = location, confidence = 1.0f, isGenuine = true)
    private var place: PlaceResolutionResult =
        PlaceResolutionResult.ExternalSuggestion(
            PlaceSuggestion("Park", "Park address", 37.0, -122.0, 100, externalId = "park"),
        )

    private fun detector() = NovelPlaceArrivalDetector(storage, familiarity, { LocationStopSnapshot(latest, fix) }, { place })

    @Test
    fun `latest movement away from the stay suppresses arrival even if the old stay is recent`() =
        runTest {
            fix = fix.copy(location = location.copy(latitude = 38.0))
            assertNull(detector().read(now, "local"))
            assertNull(storage.getString(NovelPlaceArrivalDetector.STORAGE_KEY))
        }

    @Test
    fun `old future mock and inaccurate fixes cannot establish current presence`() =
        runTest {
            val original = fix
            val bad =
                listOf(
                    original.copy(timestamp = now - 6.minutes),
                    original.copy(timestamp = now + 1.minutes),
                    original.copy(isGenuine = false),
                    original.copy(accuracyMeters = 500.0f),
                    original.copy(accuracyMeters = Float.NaN),
                )
            for (invalid in bad) {
                fix = invalid
                assertNull(detector().read(now, "local"))
            }
            fix = original
            assertEquals(arrived, detector().read(now, "local"))
        }

    @Test
    fun `unreadable familiarity cannot turn a previously known place into a novel arrival`() =
        runTest {
            storage.putString("ambient_place_familiarity", "unreadable")
            assertFailsWith<IllegalStateException> { detector().read(now, "local") }
            assertEquals("unreadable", storage.getString("ambient_place_familiarity"))
            assertNull(storage.getString(NovelPlaceArrivalDetector.STORAGE_KEY))
        }

    @Test
    fun `ten minute arrival stays novel across repeated delivery reads and reopening`() =
        runTest {
            val detector = detector()
            assertEquals(arrived, detector.read(now, "local"))
            assertEquals(arrived, detector.read(now, "local"))
            latest = listOf(stop().copy(id = "extended stay", endTime = now + 1.minutes))
            fix = fix.copy(timestamp = now + 1.minutes)
            assertEquals(arrived, detector().read(now + 1.minutes, "local"))
            assertEquals(1, familiarity.get("external:park")?.visitCount)
        }

    @Test
    fun `a known place and a later return to the same place do not trigger`() =
        runTest {
            assertEquals(arrived, detector().read(now, "local"))
            latest = listOf(stop().copy(startTime = now + 60.minutes, endTime = now + 70.minutes))
            fix = fix.copy(timestamp = now + 70.minutes)
            assertNull(detector().read(now + 70.minutes, "local"))
        }

    @Test
    fun `short stale unreliable and future stays cannot trigger`() =
        runTest {
            val invalid =
                listOf(
                    stop().copy(startTime = now - 9.minutes),
                    stop().copy(startTime = now - 30.minutes, endTime = now - 6.minutes),
                    stop().copy(hasReliableDuration = false),
                    stop().copy(endTime = now + 1.minutes),
                    stop().copy(startTime = now + 1.minutes, endTime = now + 2.minutes),
                )
            for (candidate in invalid) {
                latest = listOf(candidate)
                assertNull(detector().read(now, "local"))
            }
            latest = listOf(stop())
            assertEquals(arrived, detector().read(now, "local"))
        }

    @Test
    fun `newer observation prevents falling back to an old stay`() =
        runTest {
            latest = listOf(stop(), stop().copy(id = "left park", startTime = now, hasReliableDuration = false))
            assertNull(detector().read(now, "local"))
        }

    @Test
    fun `unknown location does not invent a novel place or consume the arrival`() =
        runTest {
            val known = place
            place = PlaceResolutionResult.UnknownLocation(location)
            assertNull(detector().read(now, "local"))
            place = known
            assertEquals(arrived, detector().read(now, "local"))
        }

    @Test
    fun `blank or changed audience cannot reuse another audiences novelty`() =
        runTest {
            assertNull(detector().read(now, ""))
            assertEquals(arrived, detector().read(now, "account-a"))
            assertNull(detector().read(now, "account-b"))
        }

    @Test
    fun `overlapping evaluations retain a single novel arrival`() =
        runTest {
            val detector = detector()
            assertEquals(List(10) { arrived }, (1..10).map { async { detector.read(now, "local") } }.awaitAll())
            assertEquals(1, familiarity.get("external:park")?.visitCount)
        }

    @Test
    fun `unreadable state is preserved rather than treated as a new arrival`() =
        runTest {
            storage.putString(NovelPlaceArrivalDetector.STORAGE_KEY, "unreadable")
            assertFailsWith<IllegalStateException> { detector().read(now, "local") }
            assertEquals("unreadable", storage.getString(NovelPlaceArrivalDetector.STORAGE_KEY))
        }

    private fun stop() =
        LocationStop(
            id = "park visit",
            location = location,
            startTime = arrived,
            endTime = now,
            sampleCount = 3,
            maxInternalGap = 5.minutes,
            hasReliableDuration = true,
            evidenceKind = LocationStopEvidenceKind.STAY,
            primaryPipeline = LocationCapturePipeline.OPTIMIZED_BACKGROUND,
        )
}
