package app.logdate.client.sync

import app.logdate.client.sync.cloud.ContentChange
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.MediaDownloadResponse
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Phones, iOS and the desktop app run [SyncProfile.Standard], which is the default everywhere. These
 * tests pin each place [SyncProfile.LinkedWatch] differs, so a change that bends the standard path
 * fails here and not on someone's phone.
 */
class StandardProfileCharacterizationTest {
    @Test
    fun `a signed out device says it is not signed in`() =
        runTest {
            val manager = testDefaultSyncManager(sessionStorage = fakeSessionStorage(authenticated = false))

            assertEquals(SyncPausedReason.NOT_SIGNED_IN, manager.getSyncStatus().pausedReason)
        }

    @Test
    fun `a signed in device with everything in place is not paused`() =
        runTest {
            val manager = testDefaultSyncManager()

            manager.fullSync()

            assertNull(manager.getSyncStatus().pausedReason)
        }

    @Test
    fun `the first sync queues everything already on the device and uploads it plainly`() =
        runTest {
            val api = fakeCloudApiClient()
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = fakeJournalNotesRepository("captured before the first sync"),
                )

            manager.fullSync()

            assertEquals(1, api.uploadContentCalls.size)
            assertTrue(api.createContentIfAbsentCalls.isEmpty())
        }

    @Test
    fun `a queued create uploads as a plain create`() =
        runTest {
            val api = fakeCloudApiClient()
            val notes = fakeJournalNotesRepository("from the phone")
            val metadata = fakeSyncMetadataService()
            metadata.enqueuePending(
                notes.allNotesObserved
                    .first()
                    .single()
                    .uid
                    .toString(),
                EntityType.NOTE,
                PendingOperation.CREATE,
            )
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                )

            manager.uploadPendingChanges()

            assertEquals(1, api.uploadContentCalls.size)
            assertTrue(api.createContentIfAbsentCalls.isEmpty())
            assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `a queued edit uploads as an update`() =
        runTest {
            val api = fakeCloudApiClient()
            val notes = fakeJournalNotesRepository("from the phone")
            val metadata = fakeSyncMetadataService()
            metadata.enqueuePending(
                notes.allNotesObserved
                    .first()
                    .single()
                    .uid
                    .toString(),
                EntityType.NOTE,
                PendingOperation.UPDATE,
            )
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                )

            manager.uploadPendingChanges()

            assertEquals(1, api.updateContentCalls.size)
            assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `a remote note is stored with its media`() =
        runTest {
            val noteId = Uuid.random().toString()
            val api = fakeCloudApiClient()
            api.downloadMediaResponse =
                Result.success(
                    MediaDownloadResponse(
                        contentId = noteId,
                        fileName = "downloaded-media.bin",
                        mimeType = "application/octet-stream",
                        sizeBytes = 4,
                        data = byteArrayOf(1, 2, 3, 4),
                        downloadUrl = "https://example.com/media/downloaded-media",
                    ),
                )
            api.getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                ContentChange(
                                    id = noteId,
                                    type = "IMAGE",
                                    mediaUri = "https://example.com/api/v1/media/image-media/binary",
                                    createdAt = CHANGED_AT,
                                    lastUpdated = CHANGED_AT,
                                    serverVersion = 1,
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = CHANGED_AT,
                    ),
                )
            val notes = FakeJournalNotesRepository()
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(api),
                    journalNotesRepository = notes,
                )

            manager.downloadRemoteChanges()

            assertEquals(1, notes.allNotesObserved.first().size)
            assertEquals(1, api.downloadMediaCalls.size)
        }

    @Test
    fun `queued links upload as plain creates`() =
        runTest {
            val api = fakeCloudApiClient()
            val metadata = fakeSyncMetadataService()
            metadata.enqueuePending(
                AssociationPendingKey(Uuid.random(), Uuid.random()).toPendingId(),
                EntityType.ASSOCIATION,
                PendingOperation.CREATE,
            )
            val manager =
                testDefaultSyncManager(
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(api),
                    syncMetadataService = metadata,
                )

            manager.uploadPendingChanges()

            assertTrue("uploadAssociations" in api.methodCalls)
            assertTrue("uploadAssociationsIfAbsent" !in api.methodCalls)
        }

    @Test
    fun `a download that completes the cloud inventory queues local records the cloud lacks`() =
        runTest {
            val scope = DownloadScope("owner", "origin")
            val database = DownloadInboxTest.Database()
            val metadata = fakeSyncMetadataService()
            val manager =
                testDefaultSyncManager(
                    journalNotesRepository = fakeJournalNotesRepository("captured on the phone and unknown to the cloud"),
                    syncMetadataService = metadata,
                    firstSyncEnqueueStore = InMemoryFirstSyncEnqueueStore().apply { markAuditedLegacyScope(scope.owner, scope.origin) },
                    downloadInbox = DownloadInbox(database, database, { scope }, { 1L }),
                )

            manager.downloadRemoteChanges()

            assertEquals(listOf(PendingOperation.CREATE), metadata.getPendingUploads(EntityType.NOTE).map { it.operation })
        }

    @Test
    fun `drafts sync on a full sync`() =
        runTest {
            val api = fakeCloudApiClient()
            val manager = testDefaultSyncManager(cloudDraftDataSource = DefaultCloudDraftDataSource(api))

            manager.fullSync()

            assertTrue("getDraftChanges" in api.methodCalls)
        }

    private companion object {
        const val CHANGED_AT = 1_710_000_000_000
    }
}
