package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryUnreadableCloudRecordStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `malformed or substituted legacy envelopes never enter automatic repair`() =
        runTest {
            val cipher = cipherFor("old")
            val substituted =
                "LDSE1:" +
                    Json.parseToJsonElement(cipher.encryptString("another-field", "original").removePrefix("LDSE2:")).jsonObject["env"]
            for (payload in listOf("LDSE1:invalid", substituted)) {
                val id = Uuid.random()
                val api =
                    fakeCloudApiClient {
                        getContentChangesResponse = Result.success(ContentChangesResponse(listOf(textChange(id, payload)), emptyList(), 10))
                    }
                val page =
                    DefaultCloudContentDataSource(api, cipherFor("current"))
                        .getContentChanges("token", Instant.fromEpochMilliseconds(0))
                        .getOrThrow()
                assertTrue(page.unreadable.isEmpty())
                assertEquals(1, page.failures.size)
            }
        }

    @Test
    fun `legacy records held on this device enter versioned repair instead of corruption`() =
        runTest {
            val oldKey = cipherFor("old")
            val currentKey = cipherFor("current")
            val heldHere = Uuid.random()
            val onlyInCloud = Uuid.random()

            fun legacy(encrypted: String): String = "LDSE1:" + Json.parseToJsonElement(encrypted.removePrefix("LDSE2:")).jsonObject["env"]
            val api = fakeCloudApiClient()
            api.getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        listOf(
                            textChange(heldHere, legacy(oldKey.encryptString(textFieldId(heldHere), "local original"))),
                            textChange(onlyInCloud, legacy(oldKey.encryptString(textFieldId(onlyInCloud), "cloud original"))),
                        ),
                        emptyList(),
                        10L,
                    ),
                )
            val notes = FakeJournalNotesRepository()
            notes.create(
                JournalNote.Text(
                    uid = heldHere,
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    content = "local original",
                ),
            )
            val metadata = fakeSyncMetadataService()
            val cloudOnly = InMemoryUnreadableCloudRecordStore()
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api, currentKey),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                    unreadableCloudRecordStore = cloudOnly,
                )

            manager.downloadRemoteChanges()

            assertEquals(listOf(heldHere.toString()), metadata.getPendingUploads(EntityType.NOTE).map { it.entityId })
            assertEquals(1L, metadata.getPendingUploads(EntityType.NOTE).single().expectedServerVersion)
            assertEquals(1, cloudOnly.count())
            assertEquals("local original", (notes.getNoteById(heldHere) as JournalNote.Text).content)
        }

    @Test
    fun `corrupt location does not silently become a successfully recovered note`() =
        runTest {
            val broken = Uuid.random()
            val readable = Uuid.random()
            val api =
                fakeCloudApiClient {
                    getContentChangesResponse =
                        Result.success(
                            ContentChangesResponse(
                                listOf(textChange(broken, "text").copy(location = "corrupt-location-marker"), textChange(readable, "text")),
                                emptyList(),
                                10,
                            ),
                        )
                }

            val page =
                DefaultCloudContentDataSource(api)
                    .getContentChanges("token", Instant.fromEpochMilliseconds(0))
                    .getOrThrow()

            assertEquals(listOf(readable), page.changes.map { it.uid })
            assertTrue(page.unreadable.isEmpty(), "Malformed location must not trigger key-recovery repair")
            assertEquals(broken.toString(), page.failures.single().entityId)
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticReason.CORRUPT_PAYLOAD, page.failures.single().reason)
        }

    @Test
    fun `an unsupported note type does not block the rest of the page`() =
        runTest {
            val unsupported = Uuid.random()
            val readable = Uuid.random()
            val api =
                fakeCloudApiClient {
                    getContentChangesResponse =
                        Result.success(
                            ContentChangesResponse(
                                listOf(textChange(unsupported, "text").copy(type = "FUTURE_TYPE"), textChange(readable, "text")),
                                emptyList(),
                                10,
                            ),
                        )
                }

            val page =
                DefaultCloudContentDataSource(api)
                    .getContentChanges("token", Instant.fromEpochMilliseconds(0))
                    .getOrThrow()

            assertEquals(listOf(readable), page.changes.map { it.uid })
            assertTrue(page.unreadable.isEmpty(), "Unknown formats must not trigger a destructive repair upload")
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticReason.UNSUPPORTED_FORMAT, page.failures.single().reason)
        }

    @Test
    fun `future encryption versions remain queued rather than becoming plaintext`() =
        runTest {
            val future = Uuid.random()
            val readable = Uuid.random()
            val api =
                fakeCloudApiClient {
                    getContentChangesResponse =
                        Result.success(
                            ContentChangesResponse(
                                listOf(textChange(future, "LDSE3:private-marker"), textChange(readable, "text")),
                                emptyList(),
                                10,
                            ),
                        )
                }
            val page =
                DefaultCloudContentDataSource(api, cipherFor("current"))
                    .getContentChanges("token", Instant.fromEpochMilliseconds(0))
                    .getOrThrow()
            assertEquals(listOf(readable), page.changes.map { it.uid })
            assertTrue(page.unreadable.isEmpty())
            assertEquals(future.toString(), page.failures.single().entityId)
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticReason.UNSUPPORTED_FORMAT, page.failures.single().reason)
        }

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
            val unreadableCloudRecordStore = InMemoryUnreadableCloudRecordStore()
            val manager: DefaultSyncManager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api, currentKey),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                    unreadableCloudRecordStore = unreadableCloudRecordStore,
                )

            val result = manager.downloadRemoteChanges()

            assertTrue(result.success, "An unreadable cloud copy is not a failed sync: ${result.errors}")
            assertNull(
                metadata.getLastSyncTime(EntityType.NOTE),
                "Without a durable inbox the feed must not advance past an unreadable record",
            )
            assertEquals(
                listOf(heldHere.toString()),
                metadata.getPendingUploads(EntityType.NOTE).filter { it.operation == PendingOperation.UPDATE }.map { it.entityId },
                "Only the record this phone holds is re-uploaded; nothing is invented for the other",
            )
            assertEquals(1, unreadableCloudRecordStore.count(), "The cloud-only record is tracked so Backup status can report it")
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
