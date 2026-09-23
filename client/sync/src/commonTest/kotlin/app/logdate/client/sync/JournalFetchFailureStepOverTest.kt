package app.logdate.client.sync

import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A journal page that always fails to *fetch* -- a persistent server error at one cursor, as
 * opposed to a record that fails to *apply* -- never got a chance to step over:
 * [SyncDownloadEngine]'s poison-page counter only saw errors from applying an already-fetched
 * page, so a fetch that throws every time left the cursor at the same timestamp forever and
 * blocked every journal from then on, across every later sync attempt.
 */
class JournalFetchFailureStepOverTest {
    @Test
    fun `a journal page that keeps failing to fetch nudges the cursor forward after a few attempts`() =
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
            assertEquals(
                Instant.fromEpochMilliseconds(1),
                metadata.getLastSyncTime(EntityType.JOURNAL),
                "The third consecutive failure should nudge the cursor forward so later syncs are not blocked forever",
            )
        }
}
