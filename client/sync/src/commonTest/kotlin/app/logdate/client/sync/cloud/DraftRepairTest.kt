package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.DraftChange
import app.logdate.shared.model.sync.DraftChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Drafts skip [app.logdate.client.sync.SyncDownloadEngine.download]'s pluggable-strategy shape
 * (they use a hand-rolled newer-wins loop instead), so the unreadable-record repair that already
 * covered journals and notes did not automatically cover drafts too. Without it, a draft
 * encrypted under a key this device no longer has failed the same way notes used to: the whole
 * sync marked as failed, forever, for a record nothing here could ever apply.
 */
class DraftRepairTest {
    @Test
    fun `an unreadable draft is set aside and does not fail the sync`() =
        runTest {
            val oldKey = cipherFor("old")
            val currentKey = cipherFor("current")
            val draftId = Uuid.random()
            val api = fakeCloudApiClient()
            api.getDraftChangesResponse =
                Result.success(
                    DraftChangesResponse(
                        drafts = listOf(draftChange(draftId, oldKey.encryptString(draftFieldId(draftId), "written before"))),
                    ),
                )
            val manager: DefaultSyncManager =
                testDefaultSyncManager(
                    cloudDraftDataSource = DefaultCloudDraftDataSource(api, currentKey),
                )

            val result = manager.syncDrafts()

            assertTrue(result.success, "An unreadable draft is not a failed sync: ${result.errors}")
        }

    @Test
    fun `the phone re-uploads its own copy of an unreadable draft`() =
        runTest {
            val oldKey = cipherFor("old")
            val currentKey = cipherFor("current")
            val heldHere = Uuid.random()
            val onlyInCloud = Uuid.random()
            val api = fakeCloudApiClient()
            api.getDraftChangesResponse =
                Result.success(
                    DraftChangesResponse(
                        drafts =
                            listOf(
                                draftChange(heldHere, oldKey.encryptString(draftFieldId(heldHere), "a")),
                                draftChange(onlyInCloud, oldKey.encryptString(draftFieldId(onlyInCloud), "b")),
                            ),
                    ),
                )
            val journalRepository = FakeJournalRepository()
            journalRepository.saveDraft(EditorDraft(id = heldHere))
            val metadata = fakeSyncMetadataService()
            metadata.clearPending()
            val manager: DefaultSyncManager =
                testDefaultSyncManager(
                    cloudDraftDataSource = DefaultCloudDraftDataSource(api, currentKey),
                    journalRepository = journalRepository,
                    syncMetadataService = metadata,
                )

            // syncDrafts() downloads (which repairs by enqueueing a CREATE) and then uploads
            // (which drains that same queue) in one call, so the queue itself is empty again by
            // the time this returns -- the upload the repair caused is what proves it happened.
            manager.syncDrafts()

            assertEquals(
                listOf(heldHere.toString()),
                api.uploadDraftCalls.map { (_, request) -> request.id },
                "Only the draft this phone holds is re-uploaded; nothing is invented for the other",
            )
            assertEquals(
                emptyList(),
                metadata.getPendingUploads(EntityType.DRAFT),
                "The repair's own upload should drain the queue it enqueued",
            )
        }

    private suspend fun cipherFor(seed: String): SyncPayloadCipher {
        val cryptoManager = TestCryptoManager()
        val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), cryptoManager)
        identityKeyManager.recoverIdentity((1..12).map { "$seed-$it" })
        return SyncPayloadCipher(ContentEncryptionService(identityKeyManager, KeyDerivation(cryptoManager), cryptoManager))
    }

    private fun draftFieldId(id: Uuid) = "sync:draft:$id:content"

    private fun draftChange(
        id: Uuid,
        content: String,
    ) = DraftChange(
        id = id.toString(),
        content = content,
        blockTypes = emptyList(),
        journalIds = emptyList(),
        createdAt = 1L,
        lastUpdated = 1L,
        deviceId = DeviceId("test-device"),
        serverVersion = 1,
    )
}
