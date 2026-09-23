package app.logdate.client.sync

import app.logdate.client.sync.metadata.InMemoryLastSyncErrorStore
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The last sync error used to live only in memory. Android restarts a backgrounded app freely, and
 * after every restart the timeline showed a calm "19 waiting" while every sync kept failing.
 */
class LastSyncErrorPersistenceTest {
    @Test
    fun `a failed sync is still reported after the app restarts`() =
        runTest {
            val store = InMemoryLastSyncErrorStore()
            val api = fakeCloudApiClient()
            api.configureContentSyncFailure(IllegalStateException("server unreachable"))
            val firstRun = TestScope(StandardTestDispatcher(testScheduler))
            testDefaultSyncManager(
                cloudContentDataSource =
                    app.logdate.client.sync.cloud
                        .DefaultCloudContentDataSource(api),
                syncScope = firstRun,
                lastErrorStore = store,
            ).downloadRemoteChanges()
            firstRun.advanceUntilIdle()

            val afterRestart = TestScope(StandardTestDispatcher(testScheduler))
            val restarted = testDefaultSyncManager(syncScope = afterRestart, lastErrorStore = store)
            afterRestart.advanceUntilIdle()

            assertEquals(
                SyncErrorType.UNKNOWN_ERROR,
                restarted.syncStatusFlow.value.lastError
                    ?.type,
            )
        }

    @Test
    fun `a sync that succeeds clears the saved error`() =
        runTest {
            val store = InMemoryLastSyncErrorStore()
            store.save(SyncError(SyncErrorType.NETWORK_ERROR, "offline"))
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val manager = testDefaultSyncManager(syncScope = scope, lastErrorStore = store)
            scope.advanceUntilIdle()

            manager.downloadRemoteChanges()
            scope.advanceUntilIdle()

            assertNull(store.load())
        }
}
