package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okio.Sink
import okio.Source
import kotlin.experimental.xor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class EncryptedSyncPayloadDataSourceTest {
    @Test
    fun `content upload encrypts text before API boundary and download decrypts it`() =
        runTest {
            val cipher = configuredCipher()
            val apiClient = RecordingPayloadCloudApiClient()
            val dataSource = DefaultCloudContentDataSource(apiClient, cipher)
            val note =
                JournalNote.Text(
                    uid = Uuid.random(),
                    content = "private journal text",
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )

            val upload = dataSource.uploadNote("token", note)

            assertTrue(upload.isSuccess)
            val uploaded = apiClient.uploadContentCalls.single().second
            assertFalse(uploaded.content == note.content, "Cloud API must not receive plaintext note content")
            assertTrue(uploaded.content.orEmpty().startsWith("LDSE1:"), "Cloud payload should use the sync E2EE envelope")

            apiClient.getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                ContentChange(
                                    id = note.uid.toString(),
                                    type = "TEXT",
                                    content = uploaded.content,
                                    mediaUri = null,
                                    createdAt = note.creationTimestamp.toEpochMilliseconds(),
                                    lastUpdated = note.lastUpdated.toEpochMilliseconds(),
                                    serverVersion = 1,
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = Clock.System.now().toEpochMilliseconds(),
                    ),
                )

            val downloaded = dataSource.getContentChanges("token", Clock.System.now()).getOrThrow()
            val downloadedNote = downloaded.changes.single() as JournalNote.Text
            assertEquals(note.content, downloadedNote.content)
        }

    @Test
    fun `journal upload encrypts metadata before API boundary and download decrypts it`() =
        runTest {
            val cipher = configuredCipher()
            val apiClient = RecordingPayloadCloudApiClient()
            val dataSource = DefaultCloudJournalDataSource(apiClient, cipher)
            val journal =
                Journal(
                    id = Uuid.random(),
                    title = "Therapy",
                    description = "raw private description",
                    created = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )

            val upload = dataSource.uploadJournal("token", journal)

            assertTrue(upload.isSuccess)
            val uploaded = apiClient.uploadJournalCalls.single().second
            assertFalse(uploaded.title == journal.title, "Cloud API must not receive plaintext journal title")
            assertFalse(uploaded.description == journal.description, "Cloud API must not receive plaintext journal description")
            assertTrue(uploaded.title.startsWith("LDSE1:"))
            assertTrue(uploaded.description.startsWith("LDSE1:"))

            apiClient.getJournalChangesResponse =
                Result.success(
                    JournalChangesResponse(
                        changes =
                            listOf(
                                JournalChange(
                                    id = journal.id.toString(),
                                    title = uploaded.title,
                                    description = uploaded.description,
                                    createdAt = journal.created.toEpochMilliseconds(),
                                    lastUpdated = journal.lastUpdated.toEpochMilliseconds(),
                                    serverVersion = 1,
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = Clock.System.now().toEpochMilliseconds(),
                    ),
                )

            val downloaded = dataSource.getJournalChanges("token", Clock.System.now()).getOrThrow()
            val downloadedJournal = downloaded.changes.single()
            assertEquals(journal.title, downloadedJournal.title)
            assertEquals(journal.description, downloadedJournal.description)
        }

    @Test
    fun `content upload fails closed when identity key is missing`() =
        runTest {
            val dataSource = DefaultCloudContentDataSource(RecordingPayloadCloudApiClient(), unconfiguredCipher())
            val note =
                JournalNote.Text(
                    uid = Uuid.random(),
                    content = "must not leak",
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )

            val result = dataSource.uploadNote("token", note)

            assertTrue(result.isFailure, "Sync must fail instead of uploading plaintext when E2EE is not configured")
        }

    @Test
    fun `draft upload encrypts content before API boundary and download decrypts it`() =
        runTest {
            val cipher = configuredCipher()
            val apiClient = RecordingPayloadCloudApiClient()
            val dataSource = DefaultCloudDraftDataSource(apiClient, cipher)
            val draft =
                EditorDraft(
                    id = Uuid.random(),
                    blocks =
                        listOf(
                            SerializableTextBlock(
                                id = Uuid.random(),
                                timestamp = Clock.System.now(),
                                content = "draft body",
                            ),
                        ),
                    createdAt = Clock.System.now(),
                    lastModifiedAt = Clock.System.now(),
                )

            val upload = dataSource.uploadDraft("token", draft, DeviceId("device-a"))

            assertTrue(upload.isSuccess)
            val uploaded = apiClient.uploadDraftCalls.single().second
            val plaintext = "draft body"
            assertFalse(uploaded.content == plaintext, "Cloud API must not receive plaintext draft content")
            assertTrue(uploaded.content.startsWith("LDSE1:"))

            apiClient.getDraftChangesResponse =
                Result.success(
                    DraftChangesResponse(
                        drafts =
                            listOf(
                                DraftChange(
                                    id = draft.id.toString(),
                                    content = uploaded.content,
                                    blockTypes = emptyList(),
                                    journalIds = emptyList(),
                                    createdAt = draft.createdAt.toEpochMilliseconds(),
                                    lastUpdated = draft.lastModifiedAt.toEpochMilliseconds(),
                                    deviceId = DeviceId("device-a"),
                                    serverVersion = 1,
                                ),
                            ),
                    ),
                )

            val downloaded = dataSource.getDraftChanges("token", Clock.System.now()).getOrThrow()
            assertEquals(plaintext, downloaded.changes.single().content)
        }

    @Test
    fun `legacy plaintext content changes still hydrate`() =
        runTest {
            val apiClient = RecordingPayloadCloudApiClient()
            val dataSource = DefaultCloudContentDataSource(apiClient, configuredCipher())
            val noteId = Uuid.random()
            apiClient.getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                ContentChange(
                                    id = noteId.toString(),
                                    type = "TEXT",
                                    content = "legacy plaintext",
                                    mediaUri = null,
                                    createdAt = Clock.System.now().toEpochMilliseconds(),
                                    lastUpdated = Clock.System.now().toEpochMilliseconds(),
                                    serverVersion = 1,
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = Clock.System.now().toEpochMilliseconds(),
                    ),
                )

            val downloaded = dataSource.getContentChanges("token", Clock.System.now()).getOrThrow()

            assertEquals("legacy plaintext", (downloaded.changes.single() as JournalNote.Text).content)
        }

    private suspend fun configuredCipher(): SyncPayloadCipher {
        val storage = InMemorySecureStorage()
        val cryptoManager = TestCryptoManager()
        val identityKeyManager = IdentityKeyManager(storage, cryptoManager)
        identityKeyManager.setupNewIdentity()
        return SyncPayloadCipher(ContentEncryptionService(identityKeyManager, KeyDerivation(cryptoManager), cryptoManager))
    }

    private fun unconfiguredCipher(): SyncPayloadCipher {
        val storage = InMemorySecureStorage()
        val cryptoManager = TestCryptoManager()
        val identityKeyManager = IdentityKeyManager(storage, cryptoManager)
        return SyncPayloadCipher(ContentEncryptionService(identityKeyManager, KeyDerivation(cryptoManager), cryptoManager))
    }
}

private class RecordingPayloadCloudApiClient : FakeCloudApiClient() {
    val uploadJournalCalls = mutableListOf<Pair<String, JournalUploadRequest>>()

    override suspend fun uploadJournal(
        accessToken: String,
        journal: JournalUploadRequest,
    ): Result<JournalUploadResponse> {
        uploadJournalCalls.add(accessToken to journal)
        return uploadJournalResponse
    }
}

private class InMemorySecureStorage : SecureStorage {
    private val storage = mutableMapOf<String, String>()
    private val updates = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun getString(key: String): String? = storage[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        storage[key] = value
        updates.value = storage.toMap()
    }

    override suspend fun remove(key: String) {
        storage.remove(key)
        updates.value = storage.toMap()
    }

    override suspend fun clear() {
        storage.clear()
        updates.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = updates.map { it[key] }

    override fun observeAll(): Flow<Map<String, String>> = updates

    override suspend fun encrypt(data: ByteArray): ByteArray = data

    override suspend fun decrypt(data: ByteArray): ByteArray? = data
}

private class TestCryptoManager : CryptoManager {
    override suspend fun generateRecoveryPhrase(): List<String> = (1..12).map { "word-$it" }

    override suspend fun deriveMasterKey(phrase: List<String>): ByteArray = pseudoHash(phrase.joinToString(",").encodeToByteArray(), 32)

    override fun validateRecoveryPhrase(phrase: List<String>): Boolean = phrase.size == 12

    override fun encryptSink(
        sink: Sink,
        key: ByteArray,
        iv: ByteArray,
    ): Sink = throw UnsupportedOperationException("Not needed for this test")

    override fun decryptSource(
        source: Source,
        key: ByteArray,
        iv: ByteArray,
    ): Source = throw UnsupportedOperationException("Not needed for this test")

    override fun generateRandomBytes(size: Int): ByteArray = ByteArray(size) { index -> (index * 13 + 7).toByte() }

    override fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray = pseudoHash(key + data, 32)

    override fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val stream = pseudoHash(key + iv + aad, plaintext.size)
        val ciphertext = ByteArray(plaintext.size) { index -> plaintext[index].xor(stream[index]) }
        val tag = pseudoHash(key + iv + aad + ciphertext, 16)
        return ciphertext + tag
    }

    override fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        require(ciphertext.size >= 16) { "Ciphertext too short" }
        val payload = ciphertext.copyOfRange(0, ciphertext.size - 16)
        val tag = ciphertext.copyOfRange(ciphertext.size - 16, ciphertext.size)
        val expectedTag = pseudoHash(key + iv + aad + payload, 16)
        require(tag.contentEquals(expectedTag)) { "Authentication failed" }
        val stream = pseudoHash(key + iv + aad, payload.size)
        return ByteArray(payload.size) { index -> payload[index].xor(stream[index]) }
    }

    private fun pseudoHash(
        input: ByteArray,
        outputSize: Int,
    ): ByteArray {
        val output = ByteArray(outputSize)
        var state = 0x6A09E667
        for (index in output.indices) {
            input.forEach { byte ->
                state = state xor byte.toInt()
                state = state * 1664525 + 1013904223
            }
            output[index] = (state ushr ((index % 4) * 8)).toByte()
        }
        return output
    }
}
