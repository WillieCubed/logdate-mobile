package app.logdate.client.sync.recovery

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.Journal
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.JournalChange
import app.logdate.shared.model.sync.JournalChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LegacyBackfillWithRetainedFailuresTest {
    @Test
    fun `retained format failures cannot strand unrelated local entries and journals`() = verifyBackfill(false)

    @Test
    fun `offline retained records are not proof of a complete inventory`() = verifyBackfill(true)

    private fun verifyBackfill(initiallyOffline: Boolean) =
        runTest {
            suspend fun cipher(seed: String): SyncPayloadCipher {
                val crypto = TestCryptoManager()
                val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
                identity.recoverIdentity((1..12).map { "$seed-$it" })
                return SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
            }
            val current = cipher("current")
            val cloudNote = Uuid.random()
            val cloudJournal = Uuid.random()
            val remoteNote =
                ContentChange(
                    id = cloudNote.toString(),
                    type = "TEXT",
                    content = "LDSE1:{\"version\":1}",
                    createdAt = 1,
                    lastUpdated = 1,
                    serverVersion = 1,
                )
            val remoteJournal =
                JournalChange(
                    id = cloudJournal.toString(),
                    title = "LDSE999:future-encrypted-journal",
                    description = "",
                    createdAt = 1,
                    lastUpdated = 1,
                    serverVersion = 1,
                )
            val api = fakeCloudApiClient()
            val notePage = ContentChangesResponse(listOf(remoteNote), emptyList(), 500)
            val journalPage = JournalChangesResponse(listOf(remoteJournal), emptyList(), 500)
            api.getContentChangesResponse = Result.success(notePage)
            api.getJournalChangesResponse = Result.success(journalPage)
            val db = DownloadInboxTest.Database()
            val scope = DownloadScope("owner", "origin")
            val inbox = DownloadInbox(db, db, { scope }, { 1L })
            val durable = DurableCloudApiClient(api, inbox)
            if (initiallyOffline) {
                durable.getContentChanges("token", 0, null).getOrThrow()
                durable.getJournalChanges("token", 0, null).getOrThrow()
                api.getContentChangesResponse = Result.failure(CloudApiException("NETWORK_ERROR", "Offline"))
                api.getJournalChangesResponse = Result.failure(CloudApiException("NETWORK_ERROR", "Offline"))
            }
            val localNote =
                JournalNote.Text(
                    Uuid.random(),
                    Instant.fromEpochMilliseconds(123),
                    Instant.fromEpochMilliseconds(124),
                    "Unsynced entry",
                )
            val localJournal =
                Journal(
                    id = Uuid.random(),
                    title = "Unsynced journal",
                    description = "",
                    created = Instant.fromEpochMilliseconds(123),
                    lastUpdated = Instant.fromEpochMilliseconds(124),
                )
            val notes = FakeJournalNotesRepository().apply { create(localNote) }
            val journals = FakeJournalRepository().apply { create(localJournal) }
            val metadata = fakeSyncMetadataService()
            val marker = InMemoryFirstSyncEnqueueStore().apply { markAuditedLegacyScope(scope.owner, scope.origin) }
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(durable, current),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(durable, current),
                    journalNotesRepository = notes,
                    journalRepository = journals,
                    syncMetadataService = metadata,
                    firstSyncEnqueueStore = marker,
                    downloadInbox = inbox,
                    transactionManager = db,
                    syncScope = backgroundScope,
                )
            if (initiallyOffline) {
                assertFalse(manager.downloadRemoteChanges().success)
                assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
                assertTrue(metadata.getPendingUploads(EntityType.JOURNAL).isEmpty())
                assertFalse(marker.hasEnqueuedLocalScope(scope.owner, scope.origin, EntityType.NOTE))
                api.getContentChangesResponse = Result.success(notePage)
                api.getJournalChangesResponse = Result.success(journalPage)
            }
            val result = manager.downloadRemoteChanges()
            if (!initiallyOffline) assertFalse(result.success, "Retained format failures must not be reported as finished")
            assertEquals(listOf(localNote.uid.toString()), metadata.getPendingUploads(EntityType.NOTE).map { it.entityId })
            assertEquals(listOf(localJournal.id.toString()), metadata.getPendingUploads(EntityType.JOURNAL).map { it.entityId })
            assertEquals(PendingOperation.CREATE, metadata.getPendingUploads(EntityType.NOTE).single().operation)
            assertEquals(localNote, notes.getNoteById(localNote.uid))
            assertEquals(localJournal, journals.getJournalById(localJournal.id))
            assertEquals(2, inbox.count(), "Encrypted original records must remain retained")
            val calls = metadata.enqueuePendingCalls.size
            manager.downloadRemoteChanges()
            assertEquals(calls, metadata.enqueuePendingCalls.size, "Repeated sync must not duplicate backfill work")
        }
}
