package app.logdate.client.sync.recovery

import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.Journal
import app.logdate.shared.model.sync.JournalUpdateRequest
import app.logdate.shared.model.sync.JournalUpdateResponse
import app.logdate.shared.model.sync.VersionConstraint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepairUploadVersionTest {
    @Test
    fun `repair uses observed remote version and rejects a later remote change`() =
        runTest {
            for (remoteVersion in listOf(2L, 3L)) {
                val journal = Journal(syncVersion = 1)
                val journals = FakeJournalRepository().apply { create(journal) }
                val queue = fakeSyncMetadataService()
                queue.enqueueRepairIfAbsent(journal.id.toString(), EntityType.JOURNAL, expectedServerVersion = 2L)
                val metadata = queue
                var observed: VersionConstraint? = null
                val api =
                    object : CloudApiClient by fakeCloudApiClient() {
                        override suspend fun updateJournal(
                            accessToken: String,
                            journalId: String,
                            journal: JournalUpdateRequest,
                        ): Result<JournalUpdateResponse> {
                            observed = journal.versionConstraint
                            return if (observed == VersionConstraint.Known(remoteVersion)) {
                                Result.success(JournalUpdateResponse(journalId, remoteVersion + 1, 10L))
                            } else {
                                Result.failure(IllegalStateException("simulated version conflict"))
                            }
                        }
                    }
                val manager =
                    testDefaultSyncManager(
                        cloudJournalDataSource = DefaultCloudJournalDataSource(api),
                        journalRepository = journals,
                        syncMetadataService = metadata,
                        syncScope = backgroundScope,
                    )
                val result = manager.uploadPendingChanges()
                assertEquals(VersionConstraint.Known(2L), observed)
                assertEquals(remoteVersion == 2L, result.success)
                if (remoteVersion == 3L) assertTrue(queue.getPendingUploads(EntityType.JOURNAL).isNotEmpty())
            }
        }
}
