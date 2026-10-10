package app.logdate.wear.v2.mark

import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
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
    private val store = MarkedMomentStore(marks, deliveries, syncMetadataService, clock = FixedClock(now))
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
    fun `a mark that cannot be saved is not queued`() =
        runTest {
            marks.failure = IllegalStateException("database closed")

            assertFailsWith<IllegalStateException> { store.create(mark) }

            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), any()) }
            assertNull(delivery())
        }

    private suspend fun delivery() =
        deliveries.get(MarkDelivery.PEER_SCOPE, MarkDelivery.DIRECTION, EntityType.MOMENT.name, mark.id.toString())

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }
}
