package app.logdate.wear.sync

import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.sync.v2.delivery.NoteSchedule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class WatchV2SchedulerTest {
    private val metadata = mockk<SyncMetadataService>(relaxed = true)
    private val layer = mockk<WearDataLayerClient>(relaxed = true)
    private val times = mutableListOf<Long>()
    private var noteSchedule: NoteSchedule = NoteSchedule.Ledger(20_000)
    private val scheduler =
        WatchSyncScheduler(
            WatchSyncWork { times += it },
            flowOf(SyncStatus(false, null, 0, false, false)),
            metadata,
            mockk(relaxed = true),
            layer,
            object : Clock {
                override fun now() = Instant.fromEpochMilliseconds(10_000)
            },
            noteSchedule = { noteSchedule },
        )

    init {
        coEvery { layer.isPhoneConnected(any()) } returns false
        coEvery { metadata.getPendingUploads(any()) } returns emptyList()
    }

    @Test
    fun `a ledger wakeup is scheduled while the phone is away`() =
        runTest {
            scheduler.scheduleNext()
            assertEquals(listOf(10_000L), times)
        }

    @Test
    fun `legacy notes cannot replace an active receipt wait with an immediate retry`() =
        runTest {
            coEvery { layer.isPhoneConnected(any()) } returns true
            coEvery { metadata.getPendingUploads(EntityType.NOTE) } returns listOf(PendingUpload("note", PendingOperation.CREATE))
            scheduler.scheduleNext()
            assertEquals(listOf(10_000L), times)
        }

    @Test
    fun `no ledger deadline does not schedule phone away legacy collections`() =
        runTest {
            noteSchedule = NoteSchedule.Ledger(null)
            coEvery { metadata.getPendingUploads(EntityType.JOURNAL) } returns listOf(PendingUpload("journal", PendingOperation.CREATE))
            scheduler.scheduleNext()
            assertEquals(emptyList(), times)
        }

    @Test
    fun `a nearby pending journal still wakes an active ledger with no notes`() =
        runTest {
            noteSchedule = NoteSchedule.Ledger(null)
            coEvery { layer.isPhoneConnected(any()) } returns true
            coEvery { metadata.getPendingUploads(EntityType.JOURNAL) } returns listOf(PendingUpload("journal", PendingOperation.CREATE))
            scheduler.scheduleNext()
            assertEquals(listOf(0L), times)
        }
}
