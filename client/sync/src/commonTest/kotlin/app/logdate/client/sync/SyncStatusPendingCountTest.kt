package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The published pending count used to refresh only when sync started, stopped, or failed. Entries
 * written between runs, or uploads that finished outside a tracked run, left the timeline showing
 * a count that no longer matched the queue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusPendingCountTest {
    @Test
    fun `a failed live queue subscription cannot leave a healthy status on screen`() =
        runTest {
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val metadata = fakeSyncMetadataService()
            val unreadable =
                object : SyncMetadataService by metadata {
                    override fun observePendingCount(): Flow<Int> = flow { error("queue observer failed") }
                }
            val manager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(),
                    syncMetadataService = unreadable,
                    syncScope = scope,
                )
            scope.advanceUntilIdle()

            assertFalse(manager.syncStatusFlow.value.queueReadable)
            assertFalse(manager.getSyncStatus().queueReadable)
        }

    @Test
    fun `an unreadable pending queue is not reported as an empty backup`() =
        runTest {
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val metadata = fakeSyncMetadataService()
            val unreadable =
                object : SyncMetadataService by metadata {
                    override suspend fun getPendingCount(): Int = error("queue unavailable")
                }
            val manager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(),
                    syncMetadataService = unreadable,
                    syncScope = scope,
                )
            scope.advanceUntilIdle()

            assertFalse(manager.syncStatusFlow.value.queueReadable)
        }

    @Test
    fun `the published count follows the queue between sync runs`() =
        runTest {
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val session = fakeSessionStorage(authenticated = true)
            val metadata = fakeSyncMetadataService(session)
            val manager =
                testDefaultSyncManager(
                    sessionStorage = session,
                    syncMetadataService = metadata,
                    syncScope = scope,
                )
            scope.advanceUntilIdle()
            assertEquals(0, manager.syncStatusFlow.value.pendingUploads)

            metadata.enqueuePending("note-1", EntityType.NOTE, PendingOperation.CREATE)
            metadata.enqueuePending("note-2", EntityType.NOTE, PendingOperation.CREATE)
            scope.advanceUntilIdle()

            assertEquals(2, manager.syncStatusFlow.value.pendingUploads)
        }
}
