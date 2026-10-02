package app.logdate.client.sync

import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

/** Fetch failures cannot authorize discarding any portion of the remote feed. */
class JournalFetchFailureStepOverTest {
    @Test
    fun `repeated fetch failure never changes the durable cursor`() =
        runTest {
            val api = fakeCloudApiClient { getJournalChangesResponse = Result.failure(Exception("boom")) }
            val metadata = fakeSyncMetadataService()
            val manager =
                testDefaultSyncManager(
                    cloudJournalDataSource = DefaultCloudJournalDataSource(api),
                    syncMetadataService = metadata,
                )

            manager.downloadRemoteChanges()
            assertNull(metadata.getLastSyncTime(EntityType.JOURNAL), "First failure should not move the cursor")

            manager.downloadRemoteChanges()
            assertNull(metadata.getLastSyncTime(EntityType.JOURNAL), "Second failure should not move the cursor either")

            manager.downloadRemoteChanges()
            assertNull(metadata.getLastSyncTime(EntityType.JOURNAL), "Repeated failure must preserve the cursor")
            repeat(10) { manager.downloadRemoteChanges() }
            assertNull(metadata.getLastSyncTime(EntityType.JOURNAL))
        }
}
