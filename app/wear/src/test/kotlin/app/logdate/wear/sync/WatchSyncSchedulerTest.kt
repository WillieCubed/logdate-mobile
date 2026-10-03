package app.logdate.wear.sync

import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [WatchSyncScheduler], which wakes the watch when an outbox item is next due.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchSyncSchedulerTest {
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val scheduled = mutableListOf<Long>()
    private val syncMetadataService = mockk<SyncMetadataService>(relaxed = true)
    private val retryScheduleStore = mockk<SyncRetryScheduleStore>(relaxed = true)
    private val dataLayerClient = mockk<WearDataLayerClient>(relaxed = true)
    private val idle = SyncStatus(isEnabled = true, lastSyncTime = null, pendingUploads = 0, isSyncing = false, hasErrors = false)
    private val status = MutableStateFlow(idle)

    init {
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
        coEvery { syncMetadataService.getPendingUploads(any()) } returns emptyList()
        coEvery { retryScheduleStore.nextAttemptAt(any(), any()) } returns null
    }

    @Test
    fun `nothing waiting schedules nothing`() =
        runTest {
            scheduler().scheduleNext()

            assertEquals(emptyList(), scheduled)
        }

    @Test
    fun `with no phone in reach nothing is scheduled`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false

            scheduler().scheduleNext()

            assertEquals(emptyList(), scheduled)
        }

    @Test
    fun `an item never attempted is due now`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")

            scheduler().scheduleNext()

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `the earliest retry time across journals, notes and links decides the run`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1", retryAt = now.toEpochMilliseconds() + 30_000)
            givenWaiting(EntityType.ASSOCIATION, "journal-1::note-2", retryAt = now.toEpochMilliseconds() + 5_000)
            givenWaiting(EntityType.JOURNAL, "journal-1", retryAt = now.toEpochMilliseconds() + 60_000)

            scheduler().scheduleNext()

            assertEquals(listOf(5_000L), scheduled)
        }

    @Test
    fun `a retry time that has passed is due now`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1", retryAt = now.toEpochMilliseconds() - 1_000)

            scheduler().scheduleNext()

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `outbox items the watch does not upload are ignored`() =
        runTest {
            givenWaiting(EntityType.DRAFT, "draft-1")

            scheduler().scheduleNext()

            assertEquals(emptyList(), scheduled)
        }

    @Test
    fun `starting schedules what is already waiting once the status settles`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")

            scheduler().start(backgroundScope)
            advanceTimeBy(2_001)

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `the phone coming into reach schedules a run`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
            status.value = idle.copy(isEnabled = false, pendingUploads = 1)
            scheduler().start(backgroundScope)
            advanceTimeBy(2_001)
            assertEquals(emptyList(), scheduled)

            coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
            status.value = idle.copy(pendingUploads = 1)
            advanceTimeBy(2_001)

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `changes during a run wait for the run to finish`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")
            status.value = idle.copy(isSyncing = true, pendingUploads = 2)
            scheduler().start(backgroundScope)
            status.value = idle.copy(isSyncing = true, pendingUploads = 1)
            advanceTimeBy(10_000)
            assertEquals(emptyList(), scheduled)

            status.value = idle.copy(pendingUploads = 1)
            advanceTimeBy(2_001)

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `a burst of changes schedules one run once the status settles`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")
            scheduler().start(backgroundScope)
            runCurrent()

            status.value = idle.copy(pendingUploads = 1)
            advanceTimeBy(1_000)
            status.value = idle.copy(pendingUploads = 2)
            advanceTimeBy(1_000)
            status.value = idle.copy(pendingUploads = 3)
            advanceTimeBy(2_001)

            assertEquals(listOf(0L), scheduled)
        }

    @Test
    fun `a sync status that cannot be read stops scheduling without failing the app`() =
        runTest {
            givenWaiting(EntityType.NOTE, "note-1")

            scheduler(syncStatus = flow<SyncStatus> { throw IllegalStateException("database closed") }).start(backgroundScope)
            advanceTimeBy(2_001)

            assertEquals(emptyList(), scheduled)
        }

    private fun scheduler(syncStatus: Flow<SyncStatus> = status): WatchSyncScheduler =
        WatchSyncScheduler(
            work = WatchSyncWork { delayMs -> scheduled += delayMs },
            syncStatus = syncStatus,
            syncMetadataService = syncMetadataService,
            retryScheduleStore = retryScheduleStore,
            dataLayerClient = dataLayerClient,
            clock =
                object : Clock {
                    override fun now(): Instant = now
                },
        )

    private fun givenWaiting(
        type: EntityType,
        entityId: String,
        retryAt: Long? = null,
    ) {
        coEvery { syncMetadataService.getPendingUploads(type) } returns listOf(PendingUpload(entityId, PendingOperation.CREATE))
        coEvery { retryScheduleStore.nextAttemptAt(type, entityId) } returns retryAt
    }
}
