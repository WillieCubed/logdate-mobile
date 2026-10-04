package app.logdate.client.sync

import app.logdate.client.sync.metadata.InMemoryIdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.InMemoryLastSyncErrorStore
import app.logdate.client.sync.metadata.InMemoryUnreadableCloudRecordStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.test.InMemorySyncConflictStore
import app.logdate.client.sync.test.fakeDataUsagePolicy
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusPublicationTest {
    @Test
    fun `an older publication cannot restore signed in state after sign out`() =
        runTest {
            val session = fakeSessionStorage()
            val blocked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var first = true
            val metadata =
                object : SyncMetadataService by fakeSyncMetadataService(session) {
                    override suspend fun getPendingCount(): Int {
                        if (first) {
                            first = false
                            blocked.complete(Unit)
                            release.await()
                        }
                        return 0
                    }
                }
            val publisher =
                SyncStatusPublisher(
                    sessionStorage = session,
                    syncMetadataService = metadata,
                    dataUsagePolicy = fakeDataUsagePolicy(),
                    cloudQuotaManager = null,
                    syncStateFlow = MutableStateFlow(SyncState.Idle),
                    lastErrorFlow = MutableStateFlow(null),
                    syncScope = CoroutineScope(backgroundScope.coroutineContext + Job().apply { cancel() }),
                    lastErrorStore = InMemoryLastSyncErrorStore(),
                    latestSyncTime = { null },
                    isEnabled = { true },
                    conflictStore = InMemorySyncConflictStore(),
                    identityRecoveryNeededStore = InMemoryIdentityRecoveryNeededStore(),
                    unreadableCloudRecordStore = InMemoryUnreadableCloudRecordStore(),
                )

            launch { publisher.publish() }
            blocked.await()
            session.clearSession()
            launch { publisher.publish() }
            runCurrent()
            release.complete(Unit)
            runCurrent()

            assertFalse(publisher.syncStatusFlow.value.isEnabled)
            assertEquals(SyncPausedReason.NOT_SIGNED_IN, publisher.syncStatusFlow.value.pausedReason)
        }
}
