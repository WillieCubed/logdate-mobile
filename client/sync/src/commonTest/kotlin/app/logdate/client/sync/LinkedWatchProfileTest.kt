package app.logdate.client.sync

import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityMintPolicy
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.ContentChange
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A linked watch runs the same sync engine as the phone with a different profile: it never creates an
 * identity key, never queues what is already on it, uploads only creates (if absent) and deletions,
 * leaves media for later, keeps only a window of remote notes, and has no drafts.
 */
class LinkedWatchProfileTest {
    private val everything = SyncProfile.LinkedWatch(RemoteApplyFilter.ALL)

    @Test
    fun `a linked watch without a key pauses for its phone and creates none`() =
        runTest {
            val identity = IdentityKeyManager(InMemorySecureStorage(), TestCryptoManager(), mintPolicy = IdentityMintPolicy.NEVER)
            val api = fakeCloudApiClient()
            val manager = testDefaultSyncManager(identityKeyManager = identity, cloudApiClient = api, profile = everything)

            manager.fullSync()

            assertFalse(identity.hasIdentityKey())
            assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, manager.getSyncStatus().pausedReason)
            assertTrue(api.methodCalls.isEmpty(), "a linked watch never asks whether the account has data")
        }

    @Test
    fun `a linked watch with its phone's key is not paused`() =
        runTest {
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(InMemorySecureStorage(), crypto, mintPolicy = IdentityMintPolicy.NEVER)
            val key = ByteArray(32) { 0x31 }
            identity.installLinkedIdentityKey(key, IdentityKeyFingerprint.of(key, crypto))
            val manager = testDefaultSyncManager(identityKeyManager = identity, profile = everything)

            manager.fullSync()

            assertNull(manager.getSyncStatus().pausedReason)
        }

    @Test
    fun `a linked watch without a session waits for its phone`() =
        runTest {
            val manager = testDefaultSyncManager(sessionStorage = fakeSessionStorage(authenticated = false), profile = everything)

            assertEquals(SyncPausedReason.NEEDS_DEVICE_LINK, manager.getSyncStatus().pausedReason)
        }

    @Test
    fun `a linked watch does not queue what it already holds`() =
        runTest {
            val api = fakeCloudApiClient()
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = fakeJournalNotesRepository("captured before linking"),
                    profile = everything,
                )

            manager.fullSync()

            assertTrue(api.uploadContentCalls.isEmpty())
            assertTrue(api.createContentIfAbsentCalls.isEmpty())
        }

    @Test
    fun `a linked watch creates notes only if absent`() =
        runTest {
            val fixture = queuedNote(PendingOperation.CREATE)

            fixture.manager.uploadPendingChanges()

            assertEquals(listOf(fixture.noteId.toString()), fixture.api.createContentIfAbsentCalls.map { it.id })
            assertTrue(fixture.api.uploadContentCalls.isEmpty())
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `an edit made on a linked watch stays queued for the phone`() =
        runTest {
            val fixture = queuedNote(PendingOperation.UPDATE)

            val result = fixture.manager.uploadPendingChanges()

            assertTrue(result.success)
            assertTrue(fixture.api.updateContentCalls.isEmpty())
            assertEquals(
                PendingOperation.UPDATE,
                fixture.metadata
                    .getPendingUploads(EntityType.NOTE)
                    .single()
                    .operation,
            )
        }

    @Test
    fun `a note the server already has is settled without taking its version`() =
        runTest {
            val fixture = queuedNote(PendingOperation.CREATE)
            fixture.api.createContentIfAbsentResponse = Result.success(ConditionalCreateResult.AlreadyExists(9))

            fixture.manager.uploadPendingChanges()

            assertEquals(1, fixture.api.createContentIfAbsentCalls.size)
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
            assertEquals(0L, fixture.notes.getNoteById(fixture.noteId)?.syncVersion)
        }

    @Test
    fun `a note deleted on another device is removed from the watch`() =
        runTest {
            val fixture = queuedNote(PendingOperation.CREATE)
            fixture.api.createContentIfAbsentResponse = Result.success(ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(5)))

            fixture.manager.uploadPendingChanges()

            assertNull(fixture.notes.getNoteById(fixture.noteId))
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `a create that fails stays queued`() =
        runTest {
            val fixture = queuedNote(PendingOperation.CREATE)
            fixture.api.createContentIfAbsentResponse = Result.failure(CloudApiException("SERVICE_UNAVAILABLE", "down", statusCode = 503))

            val result = fixture.manager.uploadPendingChanges()

            assertFalse(result.success)
            assertEquals(1, fixture.metadata.getPendingUploads(EntityType.NOTE).size)
        }

    @Test
    fun `a linked watch never syncs drafts`() =
        runTest {
            val api = fakeCloudApiClient()
            val manager = testDefaultSyncManager(cloudDraftDataSource = DefaultCloudDraftDataSource(api), profile = everything)

            manager.fullSync()

            assertFalse("getDraftChanges" in api.methodCalls)
        }

    @Test
    fun `a linked watch leaves media for later`() =
        runTest {
            val api = fakeCloudApiClient()
            api.getContentChangesResponse =
                Result.success(changes(type = "IMAGE", mediaUri = "https://example.com/api/v1/media/image-media/binary"))
            val notes = FakeJournalNotesRepository()
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(api),
                    journalNotesRepository = notes,
                    profile = everything,
                )

            manager.downloadRemoteChanges()

            assertEquals(1, notes.allNotesObserved.first().size)
            assertTrue(api.downloadMediaCalls.isEmpty())
        }

    @Test
    fun `remote notes outside the window are skipped while the download moves on`() =
        runTest {
            val api = fakeCloudApiClient()
            api.getContentChangesResponse = Result.success(changes(type = "TEXT", mediaUri = null))
            val notes = FakeJournalNotesRepository()
            val metadata = fakeSyncMetadataService()
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                    profile = SyncProfile.LinkedWatch(RemoteApplyFilter { false }),
                )

            manager.downloadRemoteChanges()

            assertTrue(notes.allNotesObserved.first().isEmpty())
            assertEquals(Instant.fromEpochMilliseconds(CHANGED_AT), metadata.getLastSyncTime(EntityType.NOTE))
        }

    @Test
    fun `a deletion made on a linked watch uploads as usual`() =
        runTest {
            val fixture = queuedNote(PendingOperation.DELETE)

            fixture.manager.uploadPendingChanges()

            assertTrue("deleteContent" in fixture.api.methodCalls)
            assertTrue(fixture.api.createContentIfAbsentCalls.isEmpty())
            assertTrue(fixture.metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    @Test
    fun `a linked watch creates journals only if absent`() =
        runTest {
            val fixture = queuedJournal()

            fixture.manager.uploadPendingChanges()

            assertTrue("createJournalIfAbsent" in fixture.api.methodCalls)
            assertFalse("uploadJournal" in fixture.api.methodCalls)
            assertTrue(fixture.metadata.getPendingUploads(EntityType.JOURNAL).isEmpty())
        }

    @Test
    fun `a journal deleted on another device is removed from the watch`() =
        runTest {
            val fixture = queuedJournal()
            fixture.api.createJournalIfAbsentResponse = Result.success(ConditionalCreateResult.Deleted(Instant.fromEpochMilliseconds(5)))

            fixture.manager.uploadPendingChanges()

            assertNull(fixture.journals.getJournalById(fixture.journal.id))
            assertTrue(fixture.metadata.getPendingUploads(EntityType.JOURNAL).isEmpty())
        }

    @Test
    fun `a linked watch creates links only if absent`() =
        runTest {
            val api = fakeCloudApiClient()
            val metadata = fakeSyncMetadataService()
            val link = AssociationPendingKey(Uuid.random(), Uuid.random())
            metadata.enqueuePending(link.toPendingId(), EntityType.ASSOCIATION, PendingOperation.CREATE)
            val manager =
                testDefaultSyncManager(
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(api),
                    syncMetadataService = metadata,
                    profile = everything,
                )

            manager.uploadPendingChanges()

            assertTrue("uploadAssociationsIfAbsent" in api.methodCalls)
            assertFalse("uploadAssociations" in api.methodCalls)
            assertTrue(metadata.getPendingUploads(EntityType.ASSOCIATION).isEmpty())
        }

    @Test
    fun `a linked watch does not backfill what the cloud inventory lacks`() =
        runTest {
            val scope = DownloadScope("owner", "origin")
            val database = DownloadInboxTest.Database()
            val metadata = fakeSyncMetadataService()
            val manager =
                testDefaultSyncManager(
                    journalNotesRepository = fakeJournalNotesRepository("captured on the watch and unknown to the cloud"),
                    syncMetadataService = metadata,
                    firstSyncEnqueueStore = InMemoryFirstSyncEnqueueStore().apply { markAuditedLegacyScope(scope.owner, scope.origin) },
                    downloadInbox = DownloadInbox(database, database, { scope }, { 1L }),
                    profile = everything,
                )

            manager.downloadRemoteChanges()

            assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        }

    private suspend fun queuedJournal(): JournalFixture {
        val api = fakeCloudApiClient()
        val journals = FakeJournalRepository()
        val journal =
            Journal(title = "from the watch", created = Instant.fromEpochMilliseconds(1), lastUpdated = Instant.fromEpochMilliseconds(1))
        journals.create(journal)
        val metadata = fakeSyncMetadataService()
        metadata.enqueuePending(journal.id.toString(), EntityType.JOURNAL, PendingOperation.CREATE)
        val manager =
            testDefaultSyncManager(
                cloudJournalDataSource = DefaultCloudJournalDataSource(api),
                journalRepository = journals,
                syncMetadataService = metadata,
                profile = everything,
            )
        return JournalFixture(api, journals, metadata, journal, manager)
    }

    private suspend fun queuedNote(operation: PendingOperation): Fixture {
        val api = fakeCloudApiClient()
        val notes = fakeJournalNotesRepository("from the watch")
        val metadata = fakeSyncMetadataService()
        val noteId =
            notes.allNotesObserved
                .first()
                .single()
                .uid
        metadata.enqueuePending(noteId.toString(), EntityType.NOTE, operation)
        val manager =
            testDefaultSyncManager(
                cloudContentDataSource = DefaultCloudContentDataSource(api),
                journalNotesRepository = notes,
                syncMetadataService = metadata,
                profile = everything,
            )
        return Fixture(api, notes, metadata, noteId, manager)
    }

    private fun changes(
        type: String,
        mediaUri: String?,
    ) = ContentChangesResponse(
        changes =
            listOf(
                ContentChange(
                    id = Uuid.random().toString(),
                    type = type,
                    content = if (mediaUri == null) "remote text" else null,
                    mediaUri = mediaUri,
                    createdAt = CHANGED_AT,
                    lastUpdated = CHANGED_AT,
                    serverVersion = 1,
                ),
            ),
        deletions = emptyList(),
        lastTimestamp = CHANGED_AT,
    )

    private class JournalFixture(
        val api: FakeCloudApiClient,
        val journals: FakeJournalRepository,
        val metadata: FakeSyncMetadataService,
        val journal: Journal,
        val manager: DefaultSyncManager,
    )

    private class Fixture(
        val api: FakeCloudApiClient,
        val notes: FakeJournalNotesRepository,
        val metadata: FakeSyncMetadataService,
        val noteId: Uuid,
        val manager: DefaultSyncManager,
    )

    private companion object {
        const val CHANGED_AT = 1_710_000_000_000
    }
}
