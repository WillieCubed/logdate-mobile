package app.logdate.wear.v2.mark

import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.capture.WatchCaptureWriteGuard
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedMomentStoreTest {
    private val marks = FakeMarkedMomentDao()
    private val deliveries = FakePeerDeliveryDao()
    private val syncMetadataService = mockk<SyncMetadataService>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(1_760_000_000_000L)
    private val store =
        MarkedMomentStore(marks, deliveries, syncMetadataService, clock = FixedClock(now), transactions = MarkTestTransactions)
    private val mark =
        MarkedMomentEntity(
            id = Uuid.random(),
            capturedAt = now,
            timeZoneId = "Europe/London",
            state = MarkedMomentState.OPEN,
            originDeviceId = "watch-1",
            lastUpdated = now,
        )

    @Test
    fun `a new mark is saved and queued for backup`() =
        runTest {
            store.create(mark)

            assertEquals(mark, marks.rows[mark.id])
            coVerify { syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE) }
        }

    @Test
    fun `the ledger records that the mark waits for a phone that takes marks`() =
        runTest {
            store.create(mark)

            val row = assertNotNull(delivery())
            assertEquals(MarkDelivery.STATE_WAITING, row.state)
            assertEquals(MarkDelivery.REASON_PEER_LACKS_MOMENTS, row.holdReason)
            assertEquals(now.toEpochMilliseconds(), row.contentVersion)
            assertEquals(0, row.attempts)
            assertNull(row.nextAttemptAt)
        }

    @Test
    fun `a change is saved and queued with its new version`() =
        runTest {
            store.create(mark)
            val later = mark.copy(heartRateBpm = 72, lastUpdated = now + 5.seconds)

            store.update(later)

            assertEquals(later, marks.rows[mark.id])
            coVerify { syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE) }
            val row = assertNotNull(delivery())
            assertEquals(later.lastUpdated.toEpochMilliseconds(), row.contentVersion)
        }

    @Test
    fun `sensor update cannot add a pending write after removal prepare`() =
        runTest {
            marks.upsert(mark)
            val preparedGuard =
                object : WatchCaptureWriteGuard {
                    override suspend fun <T> write(block: suspend () -> T): T = throw IllegalStateException("removal prepared")
                }
            val preparedStore =
                MarkedMomentStore(
                    marks,
                    deliveries,
                    syncMetadataService,
                    clock = FixedClock(now),
                    transactions = MarkTestTransactions,
                    captureGuard = preparedGuard,
                )

            assertFailsWith<IllegalStateException> { preparedStore.update(mark.copy(heartRateBpm = 72)) }
            assertEquals(mark, marks.rows[mark.id])
            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), any()) }
        }

    @Test
    fun `a mark that cannot be saved is not queued`() =
        runTest {
            marks.failure = IllegalStateException("database closed")

            assertFailsWith<IllegalStateException> { store.create(mark) }

            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), any()) }
            assertNull(delivery())
        }

    @Test
    fun `late sensors preserve phone resolution and server version`() =
        runTest {
            val resolved =
                mark.copy(
                    state = MarkedMomentState.FILLED,
                    caption = "Remember this",
                    resolvedNoteIds = Uuid.random().toString(),
                    syncVersion = 9,
                    lastUpdated =
                        now + 10.seconds,
                )
            marks.upsert(resolved)
            store.update(mark.copy(heartRateBpm = 72, lastUpdated = now + 5.seconds))
            val saved = assertNotNull(marks.rows[mark.id])
            assertEquals(MarkedMomentState.FILLED, saved.state)
            assertEquals(resolved.caption, saved.caption)
            assertEquals(resolved.resolvedNoteIds, saved.resolvedNoteIds)
            assertEquals(9L, saved.syncVersion)
            assertEquals(72, saved.heartRateBpm)
            assertEquals(now.toEpochMilliseconds() + 10_001, saved.lastUpdated.toEpochMilliseconds())
            assertEquals(saved.lastUpdated.toEpochMilliseconds(), delivery()?.contentVersion)
        }

    @Test
    fun `sensor replay cannot revive a deleted or missing capture`() =
        runTest {
            store.update(mark.copy(heartRateBpm = 72))
            assertNull(marks.rows[mark.id])
            val deleted = mark.copy(deletedAt = now + 1.seconds)
            marks.upsert(deleted)
            store.update(mark.copy(heartRateBpm = 72))
            store.create(mark)
            assertEquals(deleted, marks.rows[mark.id])
            assertNull(delivery())
            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), any()) }
        }

    @Test
    fun `duplicate creation and repeated sensors preserve the delivery attempt`() =
        runTest {
            store.create(mark)
            val sensed = mark.copy(heartRateBpm = 72, latitude = 1.0, longitude = 2.0, locationAccuracy = 3f, lastUpdated = now + 5.seconds)
            store.update(sensed)
            val waiting = assertNotNull(delivery()).copy(attempts = 3, nextAttemptAt = 9000)
            deliveries.upsert(waiting)
            store.create(mark)
            store.update(sensed)
            assertEquals(sensed, marks.rows[mark.id])
            assertEquals(waiting, delivery())
            coVerify(exactly = 1) { syncMetadataService.enqueuePending(any(), any(), PendingOperation.CREATE) }
            coVerify(exactly = 1) { syncMetadataService.enqueuePending(any(), any(), PendingOperation.UPDATE) }
        }

    @Test
    fun `sensor callback cannot change capture identity or unknown states`() =
        runTest {
            marks.upsert(mark)
            store.update(mark.copy(originDeviceId = "other-watch", heartRateBpm = 72))
            store.update(mark.copy(capturedAt = now + 1.seconds, heartRateBpm = 72))
            assertEquals(mark, marks.rows[mark.id])
            val future = mark.copy(state = "FUTURE_STATE")
            marks.upsert(future)
            store.update(mark.copy(heartRateBpm = 72))
            assertEquals(future, marks.rows[mark.id])
            assertNull(delivery())
        }

    @Test
    fun `heart rate callback does not erase the newer location`() =
        runTest {
            val current = mark.copy(latitude = 1.0, longitude = 2.0, locationAccuracy = 3f)
            marks.upsert(current)
            store.update(mark.copy(heartRateBpm = 72, lastUpdated = now + 1.seconds))
            assertEquals(current.copy(heartRateBpm = 72, lastUpdated = now + 1.seconds), marks.rows[mark.id])
        }

    @Test
    fun `late sensors cannot replace readings already stored`() =
        runTest {
            val current = mark.copy(latitude = 1.0, longitude = 2.0, locationAccuracy = 3f, heartRateBpm = 80)
            marks.upsert(current)
            store.update(
                mark.copy(latitude = 4.0, longitude = 5.0, locationAccuracy = 6f, heartRateBpm = 72, lastUpdated = now + 5.seconds),
            )
            assertEquals(current, marks.rows[mark.id])
            assertNull(delivery())
            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), any()) }
        }

    private suspend fun delivery() =
        deliveries.get(MarkDelivery.PEER_SCOPE, MarkDelivery.DIRECTION, EntityType.MOMENT.name, mark.id.toString())

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
