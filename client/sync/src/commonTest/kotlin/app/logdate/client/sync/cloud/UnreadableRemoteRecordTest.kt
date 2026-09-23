package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A cloud copy encrypted with a key this phone no longer has (the key was replaced after a restore
 * or reinstall) used to fail the whole page it arrived on. Every sync then failed on download, the
 * cursor never moved, and the background worker backed off for hours at a time -- so nothing
 * backed up at all. The phone holds the originals, so it repairs the cloud copy instead.
 */
class UnreadableRemoteRecordTest {
    @Test
    fun `a record made with another key is set aside and the rest of the page still arrives`() =
        runTest {
            val oldKey = cipherFor("old")
            val currentKey = cipherFor("current")
            val unreadable = Uuid.random()
            val readable = Uuid.random()
            val api = fakeCloudApiClient()
            api.getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                textChange(unreadable, oldKey.encryptString(textFieldId(unreadable), "written before")),
                                textChange(readable, currentKey.encryptString(textFieldId(readable), "written now")),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = 10L,
                    ),
                )

            val page =
                DefaultCloudContentDataSource(
                    api,
                    currentKey,
                ).getContentChanges("token", Instant.fromEpochMilliseconds(0)).getOrThrow()

            assertEquals(listOf(readable), page.changes.map { it.uid })
            assertEquals(listOf(unreadable), page.unreadable)
        }

    @Test
    fun `the phone re-uploads its own copy of an unreadable record and the sync succeeds`() =
        runTest {
            val oldKey = cipherFor("old")
            val currentKey = cipherFor("current")
            val heldHere = Uuid.random()
            val onlyInCloud = Uuid.random()
            val api = fakeCloudApiClient()
            api.getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                textChange(heldHere, oldKey.encryptString(textFieldId(heldHere), "a")),
                                textChange(onlyInCloud, oldKey.encryptString(textFieldId(onlyInCloud), "b")),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = 10L,
                    ),
                )
            val notes = FakeJournalNotesRepository()
            notes.create(
                JournalNote.Text(
                    uid = heldHere,
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    content = "a",
                ),
            )
            val metadata = fakeSyncMetadataService()
            metadata.clearPending()
            val manager: DefaultSyncManager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api, currentKey),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                )

            val result = manager.downloadRemoteChanges()

            assertTrue(result.success, "An unreadable cloud copy is not a failed sync: ${result.errors}")
            assertNotNull(metadata.getLastSyncTime(EntityType.NOTE), "The feed moves past the page")
            assertEquals(
                listOf(heldHere.toString()),
                metadata.getPendingUploads(EntityType.NOTE).filter { it.operation == PendingOperation.CREATE }.map { it.entityId },
                "Only the record this phone holds is re-uploaded; nothing is invented for the other",
            )
        }

    private suspend fun cipherFor(seed: String): SyncPayloadCipher {
        val cryptoManager = TestCryptoManager()
        val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), cryptoManager)
        identityKeyManager.recoverIdentity((1..12).map { "$seed-$it" })
        return SyncPayloadCipher(
            ContentEncryptionService(identityKeyManager, KeyDerivation(cryptoManager), cryptoManager),
            identityKeyManager,
            cryptoManager,
        )
    }

    private fun textFieldId(id: Uuid) = "sync:note:$id:text"

    private fun textChange(
        id: Uuid,
        content: String,
    ) = ContentChange(
        id = id.toString(),
        type = "TEXT",
        content = content,
        mediaUri = null,
        createdAt = 1L,
        lastUpdated = 1L,
        serverVersion = 1,
    )
}
