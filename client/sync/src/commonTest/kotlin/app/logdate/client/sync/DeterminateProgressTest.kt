package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.ContentUploadRequest
import app.logdate.client.sync.cloud.ContentUploadResponse
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Progress was a live count of what was left, which only ever shrinks and never says how far
 * through the run it is. On a first sync of several hundred entries the difference between "a
 * moment" and "an hour" is exactly the denominator that was being thrown away.
 *
 * The total is knowable the instant a run starts: it is what is queued at that point.
 */
@OptIn(ExperimentalUuidApi::class)
class DeterminateProgressTest {
    @Test
    fun `a run reports how many items it started with and not just how many are left`() =
        runTest {
            val notesRepository = fakeJournalNotesRepository()
            val syncMetadataService = fakeSyncMetadataService()
            repeat(3) { index ->
                val note =
                    JournalNote.Text(
                        uid = Uuid.random(),
                        content = "note $index",
                        creationTimestamp = Clock.System.now(),
                        lastUpdated = Clock.System.now(),
                    )
                notesRepository.create(note)
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            val syncManager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(),
                    journalNotesRepository = notesRepository,
                    syncMetadataService = syncMetadataService,
                )

            syncManager.uploadPendingChanges()

            val status = syncManager.getSyncStatus()
            assertEquals(3, status.totalForRun, "the run should remember what it set out to upload")
            assertEquals(3, status.completedInRun, "and how much of that it got through")
        }

    @Test
    fun `a run that had nothing queued reports no total rather than zero of zero`() =
        runTest {
            val syncManager = testDefaultSyncManager(sessionStorage = fakeSessionStorage())

            syncManager.uploadPendingChanges()

            assertNull(syncManager.getSyncStatus().totalForRun)
        }

    @Test
    fun `completedInRun advances after each item rather than jumping from zero to the total`() =
        runTest {
            val notesRepository = fakeJournalNotesRepository()
            val syncMetadataService = fakeSyncMetadataService()
            repeat(3) { index ->
                val note =
                    JournalNote.Text(
                        uid = Uuid.random(),
                        content = "note $index",
                        creationTimestamp = Clock.System.now(),
                        lastUpdated = Clock.System.now(),
                    )
                notesRepository.create(note)
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }

            // Snapshots completedInRun as the sync manager itself sees it at the moment each note
            // reaches the network, rather than relying on flow-collection timing.
            lateinit var syncManager: SyncManager
            val completedBeforeEachUpload = mutableListOf<Int?>()
            val cloudApiClient =
                object : FakeCloudApiClient() {
                    override suspend fun uploadContent(
                        accessToken: String,
                        content: ContentUploadRequest,
                    ): Result<ContentUploadResponse> {
                        completedBeforeEachUpload.add(syncManager.getSyncStatus().completedInRun)
                        return super.uploadContent(accessToken, content)
                    }
                }

            syncManager =
                testDefaultSyncManager(
                    sessionStorage = fakeSessionStorage(),
                    journalNotesRepository = notesRepository,
                    syncMetadataService = syncMetadataService,
                    cloudContentDataSource = DefaultCloudContentDataSource(cloudApiClient),
                )

            syncManager.uploadPendingChanges()

            assertEquals(
                listOf<Int?>(0, 1, 2),
                completedBeforeEachUpload,
                "each upload should see one more item completed than the last, not zero for the whole run",
            )
        }
}
