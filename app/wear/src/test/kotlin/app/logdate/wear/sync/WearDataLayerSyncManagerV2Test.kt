package app.logdate.wear.sync

import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotDataMapper
import app.logdate.client.sync.datalayer.JournalDataMapper
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.sync.v2.delivery.WatchNotesV2
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WearDataLayerSyncManagerV2Test {
    private val layer = mockk<WearDataLayerClient>(relaxed = true)
    private val metadata = mockk<SyncMetadataService>(relaxed = true)
    private val letters = mockk<SyncDeadLetterStore>(relaxed = true)
    private val health = mockk<app.logdate.client.database.dao.HealthSnapshotDao>(relaxed = true)

    init {
        coEvery { layer.isPhoneConnected(any()) } returns true
        coEvery { metadata.getPendingUploads(any()) } returns emptyList()
        every { metadata.observePendingCount() } returns flowOf(0)
        every { letters.observe() } returns flowOf(emptyList())
        coEvery { health.getAfter(any()) } returns emptyList()
    }

    @Test
    fun `the active runner can deliver while the phone is away`() =
        runTest {
            coEvery { layer.isPhoneConnected(any()) } returns false
            var called = false
            val result =
                manager(
                    WatchNotesV2 {
                        called = true
                        SyncResult(success = true, uploadedItems = 1)
                    },
                ).uploadPendingChanges()
            assertTrue(called)
            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)
        }

    @Test
    fun `an inactive runner preserves legacy note delivery`() =
        runTest {
            val result = manager(WatchNotesV2 { null }).uploadPendingChanges()
            assertTrue(result.success)
            assertEquals(0, result.uploadedItems)
        }

    @Test
    fun `runner errors are returned unchanged`() =
        runTest {
            val error =
                SyncResult(
                    success = false,
                    errors = listOf(SyncError(SyncErrorType.NETWORK_ERROR, "Send failed", retryable = true)),
                )
            assertSame(error, manager(WatchNotesV2 { error }).syncContent())
        }

    @Test
    fun `a full run includes the new note result alongside legacy collections`() =
        runTest {
            val result = manager(WatchNotesV2 { SyncResult(success = true, uploadedItems = 2) }).fullSync()
            assertTrue(result.success)
            assertEquals(2, result.uploadedItems)
        }

    @Test
    fun `new note delivery shares the existing run lock with journal delivery`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val sync =
                manager(
                    WatchNotesV2 {
                        started.complete(Unit)
                        release.await()
                        SyncResult(success = true)
                    },
                )
            val notes = backgroundScope.async { sync.uploadPendingChanges() }
            runCurrent()
            try {
                assertTrue(started.isCompleted)
                val journals = backgroundScope.async { sync.syncJournals() }
                runCurrent()
                assertFalse(journals.isCompleted)
                release.complete(Unit)
                assertTrue(notes.await().success)
                assertTrue(journals.await().success)
            } finally {
                release.complete(Unit)
            }
        }

    private fun TestScope.manager(notes: WatchNotesV2) =
        WearDataLayerSyncManager(
            dataLayerClient = layer,
            syncMetadataService = metadata,
            retryScheduleStore = mockk(relaxed = true),
            deadLetterStore = letters,
            notesRepository = mockk(relaxed = true),
            journalRepository = mockk(relaxed = true),
            healthSnapshotDao = health,
            noteDataMapper =
                app.logdate.client.sync.datalayer
                    .NoteDataMapper(),
            journalDataMapper = JournalDataMapper(),
            associationDataMapper = AssociationDataMapper(),
            healthSnapshotDataMapper = HealthSnapshotDataMapper(),
            mediaFiles = mockk(),
            phoneReachability = PhoneReachability { flowOf(null) },
            scope = backgroundScope,
            notesV2 = notes,
        )
}
