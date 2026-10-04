package app.logdate.client.sync.recovery

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.ContentChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LegacyLocalCoverageTest {
    @Test
    fun `an inventory page that cannot advance is incomplete and cannot start local backfill`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val scope = DownloadScope("owner", "origin")
            val inbox = DownloadInbox(db, db, { scope }, { 1L })
            inbox.stage("NOTE", 500, emptyList())
            val metadata = fakeSyncMetadataService()
            val api =
                fakeCloudApiClient().apply {
                    getContentChangesResponse = Result.success(ContentChangesResponse(emptyList(), emptyList(), 500, hasMore = true))
                }
            val marker = InMemoryFirstSyncEnqueueStore().apply { markAuditedLegacyScope(scope.owner, scope.origin) }
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(DurableCloudApiClient(api, inbox)),
                    journalNotesRepository = FakeJournalNotesRepository().apply { addTestNote("Legacy entry") },
                    syncMetadataService = metadata,
                    firstSyncEnqueueStore = marker,
                    downloadInbox = inbox,
                    syncScope = backgroundScope,
                )

            assertFalse(manager.downloadRemoteChanges().success)
            assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `an advanced cursor does not strand a local legacy entry missing from the cloud inventory`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val scope = DownloadScope("owner", "origin")
            val inbox = DownloadInbox(db, db, { scope }, { 1L })
            val missing = Uuid.random()
            val cloud = Uuid.random()
            val deleted = Uuid.random()
            inbox.stage(
                "NOTE",
                500,
                listOf(WireDownload(cloud.toString(), 10, false, "wire"), WireDownload(deleted.toString(), 11, true, "")),
            )
            val notes = FakeJournalNotesRepository()
            val date = Instant.fromEpochMilliseconds(123)
            for (id in listOf(missing, cloud, deleted)) notes.create(JournalNote.Text(id, date, date, "Original entry"))
            val metadata = fakeSyncMetadataService()
            metadata.updateLastSyncTime(EntityType.NOTE, Instant.fromEpochMilliseconds(500))
            val marker = InMemoryFirstSyncEnqueueStore().apply { markAuditedLegacyScope(scope.owner, scope.origin) }
            val api =
                fakeCloudApiClient().apply {
                    getContentChangesResponse = Result.success(ContentChangesResponse(emptyList(), emptyList(), 500))
                }
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(DurableCloudApiClient(api, inbox)),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                    firstSyncEnqueueStore = marker,
                    downloadInbox = inbox,
                    syncScope = backgroundScope,
                )

            assertTrue(manager.downloadRemoteChanges().success)
            assertEquals(listOf(missing.toString()), metadata.getPendingUploads(EntityType.NOTE).map { it.entityId })
            assertEquals(PendingOperation.CREATE, metadata.getPendingUploads(EntityType.NOTE).single().operation)
            assertEquals(date, notes.getNoteById(missing)?.creationTimestamp)
            val calls = metadata.enqueuePendingCalls.size
            manager.downloadRemoteChanges()
            assertEquals(calls, metadata.enqueuePendingCalls.size)
        }

    @Test
    fun `failed inventory download cannot enqueue records from an incomplete cloud view`() =
        runTest {
            val db = DownloadInboxTest.Database()
            val scope = DownloadScope("owner", "origin")
            val inbox = DownloadInbox(db, db, { scope }, { 1L })
            val notes = FakeJournalNotesRepository().apply { addTestNote("Legacy entry") }
            val metadata = fakeSyncMetadataService()
            val marker = InMemoryFirstSyncEnqueueStore().apply { markAuditedLegacyScope(scope.owner, scope.origin) }
            val api =
                fakeCloudApiClient().apply {
                    getContentChangesResponse =
                        Result.failure(
                            CloudApiException("NETWORK_ERROR", "Offline"),
                        )
                }
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(DurableCloudApiClient(api, inbox)),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                    firstSyncEnqueueStore = marker,
                    downloadInbox = inbox,
                    syncScope = backgroundScope,
                )

            manager.downloadRemoteChanges()
            assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
            api.getContentChangesResponse = Result.success(ContentChangesResponse(emptyList(), emptyList(), 500))
            manager.downloadRemoteChanges()
            assertEquals(1, metadata.getPendingUploads(EntityType.NOTE).size)
        }
}
