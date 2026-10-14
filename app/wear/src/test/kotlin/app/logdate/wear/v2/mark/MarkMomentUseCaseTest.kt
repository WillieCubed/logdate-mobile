package app.logdate.wear.v2.mark

import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.health.HeartRateSampler
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MarkMomentUseCaseTest {
    private val marks = FakeMarkedMomentDao()
    private val syncMetadataService = mockk<SyncMetadataService>(relaxed = true)
    private val clock = SteppingClock(Instant.fromEpochMilliseconds(1_760_000_000_000L))
    private var place: NoteLocation? = null
    private var bpm: Int? = null

    @Test
    fun `deferred cold mark preserves its click time zone and stable identity`() =
        runTest {
            val clickedAt = clock.start - 30.seconds
            val id = kotlin.uuid.Uuid.random()
            val mark = useCase().mark(clickedAt, "America/Los_Angeles", id)
            assertEquals(clickedAt, mark.capturedAt)
            assertEquals("America/Los_Angeles", mark.timeZoneId)
            assertEquals(id, mark.id)
            useCase().mark(clickedAt, "America/Los_Angeles", id)
            assertEquals(1, marks.rows.size)
        }

    @Test
    fun `a mark is saved at once with its time and zone and this watch`() =
        runTest {
            val mark = useCase().mark()

            assertEquals(mark, marks.rows[mark.id])
            assertEquals(clock.start, mark.capturedAt)
            assertEquals("Europe/Paris", mark.timeZoneId)
            assertEquals("watch-1", mark.originDeviceId)
            assertEquals(MarkedMomentState.OPEN, mark.state)
            coVerify { syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE) }
        }

    @Test
    fun `the place and heart rate are added when they can be read`() =
        runTest {
            place = NoteLocation(coordinates = NoteCoordinates(latitude = 37.77, longitude = -122.41, accuracy = 12f))
            bpm = 72
            val useCase = useCase()
            val mark = useCase.mark()
            clock.advance(5.seconds)

            val enriched = useCase.enrich(mark)

            assertEquals(37.77, enriched.latitude)
            assertEquals(-122.41, enriched.longitude)
            assertEquals(12f, enriched.locationAccuracy)
            assertEquals(72, enriched.heartRateBpm)
            assertEquals(clock.start + 5.seconds, enriched.lastUpdated)
            assertEquals(enriched, marks.rows[mark.id])
            coVerify { syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE) }
        }

    @Test
    fun `a mark with nothing to add is left as it is`() =
        runTest {
            val useCase = useCase()
            val mark = useCase.mark()

            assertEquals(mark, useCase.enrich(mark))

            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), PendingOperation.UPDATE) }
        }

    @Test
    fun `a slow place reading is given up after three seconds`() =
        runTest {
            bpm = 70
            val useCase =
                useCase(
                    readPlace = {
                        delay(60_000)
                        place
                    },
                )
            val mark = useCase.mark()

            val enriched = async { useCase.enrich(mark) }
            advanceTimeBy(MarkMomentUseCase.PLACE_TIMEOUT_MS + 1)

            assertTrue(enriched.isCompleted)
            assertNull(enriched.await().latitude)
            assertEquals(70, enriched.await().heartRateBpm)
        }

    @Test
    fun `a place that cannot be read does not lose the heart rate`() =
        runTest {
            bpm = 70
            val useCase = useCase(readPlace = { throw IllegalStateException("location settings unreadable") })
            val mark = useCase.mark()

            val enriched = useCase.enrich(mark)

            assertNull(enriched.latitude)
            assertEquals(70, enriched.heartRateBpm)
            assertEquals(enriched, marks.rows[mark.id])
        }

    @Test
    fun `a heart rate sensor that fails does not lose the place`() =
        runTest {
            place = NoteLocation(coordinates = NoteCoordinates(latitude = 37.77, longitude = -122.41))
            val useCase = useCase(sensor = HeartRateSampler { throw IllegalStateException("sensor service died") })
            val mark = useCase.mark()

            val enriched = useCase.enrich(mark)

            assertEquals(37.77, enriched.latitude)
            assertNull(enriched.heartRateBpm)
            assertEquals(enriched, marks.rows[mark.id])
        }

    @Test
    fun `enrichment returns the current resolved mark rather than the old capture`() =
        runTest {
            bpm = 72
            val useCase = useCase()
            val capture = useCase.mark()
            val resolved = capture.copy(state = MarkedMomentState.FILLED, caption = "Resolved on phone", syncVersion = 7)
            marks.upsert(resolved)
            clock.advance(5.seconds)
            val enriched = useCase.enrich(capture)
            assertEquals(resolved.copy(heartRateBpm = 72, lastUpdated = clock.start + 5.seconds), enriched)
            assertEquals(enriched, marks.rows[capture.id])
        }

    private fun useCase(
        readPlace: suspend () -> NoteLocation? = { place },
        sensor: HeartRateSampler = HeartRateSampler { bpm },
    ) = MarkMomentUseCase(
        store = MarkedMomentStore(marks, FakePeerDeliveryDao(), syncMetadataService, clock, MarkTestTransactions),
        deviceId = { "watch-1" },
        place = readPlace,
        heartRate = sensor,
        clock = clock,
        timeZoneId = { "Europe/Paris" },
    )

    private class SteppingClock(
        val start: Instant,
    ) : Clock {
        private var current = start

        fun advance(by: Duration) {
            current += by
        }

        override fun now(): Instant = current
    }
}
