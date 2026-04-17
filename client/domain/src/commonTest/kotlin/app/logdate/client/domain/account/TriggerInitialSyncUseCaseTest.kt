package app.logdate.client.domain.account

import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class TriggerInitialSyncUseCaseTest {
    @Test
    fun `returns Success when fullSync succeeds cleanly`() =
        runTest {
            val manager = FakeSyncManager(fullSyncResult = SyncResult(success = true))
            val useCase = TriggerInitialSyncUseCase(manager)

            assertIs<TriggerInitialSyncUseCase.Result.Success>(useCase())
            assertTrue(manager.fullSyncCalled)
            assertTrue(manager.syncCalled)
        }

    @Test
    fun `returns Partial when fullSync reports errors`() =
        runTest {
            val errors =
                listOf(
                    SyncError(type = SyncErrorType.NETWORK_ERROR, message = "flaky"),
                )
            val manager =
                FakeSyncManager(
                    fullSyncResult =
                        SyncResult(
                            success = false,
                            uploadedItems = 3,
                            downloadedItems = 5,
                            errors = errors,
                        ),
                )
            val useCase = TriggerInitialSyncUseCase(manager)

            val result = useCase()
            val partial = assertIs<TriggerInitialSyncUseCase.Result.Partial>(result)
            assertEquals(3, partial.uploadedItems)
            assertEquals(5, partial.downloadedItems)
            assertEquals(listOf("flaky"), partial.errorMessages)
        }

    @Test
    fun `returns TimedOut when fullSync does not complete within the budget`() =
        runTest {
            val manager = NeverCompletingSyncManager()
            val useCase = TriggerInitialSyncUseCase(manager, timeout = 50.milliseconds)

            assertIs<TriggerInitialSyncUseCase.Result.TimedOut>(useCase())
        }

    @Test
    fun `returns Error when fullSync throws`() =
        runTest {
            val manager = ThrowingSyncManager()
            val useCase = TriggerInitialSyncUseCase(manager)

            val result = useCase()
            val error = assertIs<TriggerInitialSyncUseCase.Result.Error>(result)
            assertEquals("boom", error.message)
        }

    private open class FakeSyncManager(
        private val fullSyncResult: SyncResult,
    ) : SyncManager {
        var fullSyncCalled: Boolean = false
        var syncCalled: Boolean = false

        override val syncStatusFlow: StateFlow<SyncStatus> =
            MutableStateFlow(
                SyncStatus(
                    isEnabled = true,
                    lastSyncTime = null,
                    pendingUploads = 0,
                    isSyncing = false,
                    hasErrors = false,
                ),
            )

        override fun sync(startNow: Boolean) {
            syncCalled = true
        }

        override suspend fun uploadPendingChanges(): SyncResult = SyncResult(success = true)

        override suspend fun downloadRemoteChanges(): SyncResult = SyncResult(success = true)

        override suspend fun syncContent(): SyncResult = SyncResult(success = true)

        override suspend fun syncJournals(): SyncResult = SyncResult(success = true)

        override suspend fun syncAssociations(): SyncResult = SyncResult(success = true)

        override suspend fun syncDrafts(): SyncResult = SyncResult(success = true)

        override suspend fun fullSync(): SyncResult {
            fullSyncCalled = true
            return fullSyncResult
        }

        override suspend fun getSyncStatus(): SyncStatus =
            SyncStatus(
                isEnabled = true,
                lastSyncTime = null,
                pendingUploads = 0,
                isSyncing = false,
                hasErrors = false,
            )
    }

    private class NeverCompletingSyncManager : FakeSyncManager(SyncResult(success = true)) {
        private val never = CompletableDeferred<SyncResult>()

        override suspend fun fullSync(): SyncResult = never.await()
    }

    private class ThrowingSyncManager : FakeSyncManager(SyncResult(success = true)) {
        override suspend fun fullSync(): SyncResult = throw RuntimeException("boom")
    }
}
