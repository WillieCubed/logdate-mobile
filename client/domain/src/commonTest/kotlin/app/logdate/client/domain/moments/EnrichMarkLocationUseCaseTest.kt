package app.logdate.client.domain.moments

import app.logdate.client.domain.export.support.RoundTripLocationHistoryRepository
import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import app.logdate.client.domain.watch.DefaultWatchPreferencesRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [EnrichMarkLocationUseCase]: a mark captured without a place takes the nearest accurate,
 * genuine location sample within ten minutes, and only when places are on.
 */
class EnrichMarkLocationUseCaseTest {
    private val capturedAt = Instant.parse("2026-10-10T22:42:00Z")
    private val now = Instant.parse("2026-10-10T23:00:00Z")
    private val marks = FakeMarks()
    private val history = RoundTripLocationHistoryRepository()
    private val preferences = DefaultWatchPreferencesRepository(StampedPreferenceStore(FakeKeyValueStorage(), PeerRole.PHONE))
    private val enrich =
        EnrichMarkLocationUseCase(
            marks = marks,
            locationHistory = history,
            watchPreferences = preferences,
            clock =
                object : Clock {
                    override fun now(): Instant = now
                },
        )

    @Test
    fun `nothing is filled while places are off`() =
        runTest {
            val mark = marks.add(mark())
            history.entries = listOf(sample(offset = 1.minutes, accuracy = 10f))

            assertEquals(MarkLocationResult.PlacesOff, enrich(mark.id))
            assertTrue(marks.fills.isEmpty())
        }

    @Test
    fun `a mark that has a place is left alone`() =
        runTest {
            placesOn()
            val mark = marks.add(mark().copy(location = MarkLocation(1.0, 2.0, 5f)))

            assertEquals(MarkLocationResult.AlreadyPlaced, enrich(mark.id))
        }

    @Test
    fun `a missing mark is reported`() =
        runTest {
            placesOn()

            assertEquals(MarkLocationResult.MarkNotFound, enrich(Uuid.random()))
        }

    @Test
    fun `the nearest accurate genuine sample within ten minutes fills the mark`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries =
                listOf(
                    sample(offset = 3.minutes, accuracy = 40f, latitude = 37.10),
                    sample(offset = (-1).minutes, accuracy = 150f, latitude = 37.20),
                    sample(offset = (-11).minutes, accuracy = 5f, latitude = 37.30),
                    sample(offset = 30.seconds, accuracy = 5f, latitude = 37.40, isMock = true),
                    sample(offset = (-2).minutes, accuracy = 5f, latitude = 37.50, isGenuine = false),
                    sample(offset = (-1).minutes, accuracy = null, latitude = 37.60),
                )

            val expected = MarkLocation(latitude = 37.10, longitude = LONGITUDE, accuracyMeters = 40f)
            assertEquals(MarkLocationResult.Filled(expected), enrich(mark.id))
            assertEquals(listOf(Triple(mark.id, expected, now)), marks.fills)
        }

    @Test
    fun `ten minutes away and 100 meters still count`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries = listOf(sample(offset = 10.minutes, accuracy = 100f, latitude = 37.70))

            assertEquals(MarkLocationResult.Filled(MarkLocation(37.70, LONGITUDE, 100f)), enrich(mark.id))
        }

    @Test
    fun `a tie in time goes to the more accurate sample`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries =
                listOf(
                    sample(offset = (-4).minutes, accuracy = 50f, latitude = 37.80),
                    sample(offset = 4.minutes, accuracy = 20f, latitude = 37.90),
                )

            assertEquals(MarkLocationResult.Filled(MarkLocation(37.90, LONGITUDE, 20f)), enrich(mark.id))
        }

    @Test
    fun `no usable sample leaves the mark as it is`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries = listOf(sample(offset = 1.minutes, accuracy = 250f))

            assertEquals(MarkLocationResult.NoNearbySample, enrich(mark.id))
            assertTrue(marks.fills.isEmpty())
        }

    @Test
    fun `a place filled elsewhere first is not overwritten`() =
        runTest {
            placesOn()
            val mark = marks.add(mark())
            history.entries = listOf(sample(offset = 1.minutes, accuracy = 10f))
            marks.fillSucceeds = false

            assertEquals(MarkLocationResult.AlreadyPlaced, enrich(mark.id))
        }

    private suspend fun placesOn() {
        preferences.set(SharedPreferenceKey.CapturePlaces, true)
    }

    private fun mark() =
        MarkedMoment(
            id = Uuid.random(),
            capturedAt = capturedAt,
            timeZoneId = "America/Los_Angeles",
            location = null,
            heartRateBpm = null,
            state = MarkState.OPEN,
            caption = "",
            resolvedNoteIds = emptySet(),
            originDeviceId = "watch",
            lastUpdated = capturedAt,
            syncVersion = 0,
        )

    private fun sample(
        offset: Duration,
        accuracy: Float?,
        latitude: Double = 37.77,
        isGenuine: Boolean = true,
        isMock: Boolean = false,
    ) = LocationHistoryItem(
        userId = "user",
        deviceId = "phone",
        timestamp = capturedAt + offset,
        location = Location(latitude = latitude, longitude = LONGITUDE, altitude = LocationAltitude(10.0, AltitudeUnit.METERS)),
        confidence = 0.9f,
        isGenuine = isGenuine,
        accuracyMeters = accuracy,
        isMock = isMock,
    )

    private class FakeMarks : MarkedMomentRepository {
        override suspend fun dismiss(
            id: Uuid,
            updatedAt: Instant,
        ): Boolean = error("Not used")

        override suspend fun fill(
            id: Uuid,
            caption: String,
            noteIds: Set<Uuid>,
            updatedAt: Instant,
        ): Boolean = error("Not used")

        override suspend fun delete(
            id: Uuid,
            deletedAt: Instant,
        ): Boolean = error("Not used")

        private val marks = mutableMapOf<Uuid, MarkedMoment>()
        val fills = mutableListOf<Triple<Uuid, MarkLocation, Instant>>()
        var fillSucceeds = true

        fun add(mark: MarkedMoment): MarkedMoment {
            marks[mark.id] = mark
            return mark
        }

        override fun observeOpenMarks(): Flow<List<MarkedMoment>> = flowOf(marks.values.filter { it.state == MarkState.OPEN })

        override suspend fun getMark(id: Uuid): MarkedMoment? = marks[id]

        override suspend fun fillLocationIfMissing(
            id: Uuid,
            location: MarkLocation,
            updatedAt: Instant,
        ): Boolean {
            if (!fillSucceeds) return false
            fills += Triple(id, location, updatedAt)
            return true
        }
    }

    private companion object {
        const val LONGITUDE = -122.48
    }
}
