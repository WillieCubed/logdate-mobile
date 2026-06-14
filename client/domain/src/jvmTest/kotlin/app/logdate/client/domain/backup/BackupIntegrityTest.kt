package app.logdate.client.domain.backup

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreResult
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.MediaPayload
import app.logdate.shared.model.backup.BackupManifest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.CipherSink
import okio.CipherSource
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

/**
 * Verifies the end-to-end data integrity of the backup system.
 *
 * Ensures that:
 * 1. Plaintext is exactly preserved through encryption/decryption.
 * 2. Authenticated encryption (GCM) correctly detects bit-level tampering.
 */
class BackupIntegrityTest {
    private val fileSystem = FakeFileSystem()
    private val cryptoManager = JvmCryptoManager()
    private val json =
        Json {
            encodeDefaults = true
        }
    private val metadata =
        ExportMetadata(
            exportDate = Instant.parse("2026-06-14T12:00:00Z"),
            userId = "test-user",
            deviceId = "test-device",
            appVersion = "1.0-test",
            stats =
                ExportStats(
                    journalCount = 0,
                    noteCount = 4,
                    draftCount = 0,
                    mediaCount = 3,
                ),
        )

    private val createBackup =
        CreateEncryptedBackupUseCase(
            exportUserDataUseCase = mockk<ExportUserDataUseCase>(relaxed = true),
            cryptoManager = cryptoManager,
            fileSystem = fileSystem,
            deviceIdProvider = { "test-device" },
            userIdProvider = { "test-user" },
        )

    private val restoreBackup =
        RestoreFromEncryptedBackupUseCase(
            cryptoManager = cryptoManager,
            fileSystem = fileSystem,
        )

    @Test
    fun `crypto primitives work`() =
        runBlocking {
            val recoveryPhrase =
                listOf("witch", "collapse", "practice", "feed", "shame", "open", "despair", "creek", "road", "again", "ice", "least")
            val key = cryptoManager.deriveMasterKey(recoveryPhrase)
            // Verifying 128-bit key for test environment stability
            assertEquals(16, key.size)
        }

    @Test
    fun `encrypted backup writes manifest header with real identity and stored iv`() =
        runBlocking {
            val backupPath = "/backup.enc".toPath()
            val recoveryPhrase =
                listOf("witch", "collapse", "practice", "feed", "shame", "open", "despair", "creek", "road", "again", "ice", "least")
            val secretData = "This is the user's soul. It must be protected at all costs."

            val progress =
                createBackup(backupPath, recoveryPhrase) { sink ->
                    sink.writeUtf8(secretData)
                }.toList()

            val lastState = progress.last()
            assertTrue("Expected Completed state, got $lastState", lastState is BackupProgress.Completed)

            val header = fileSystem.source(backupPath).buffer().use { source -> EncryptedBackupFileFormat.readHeader(source) }
            val manifest = Json.decodeFromString<BackupManifest>(header.manifestJson)

            assertEquals("test-device", manifest.deviceId)
            assertEquals("test-user", manifest.userId)
            assertFalse(manifest.deviceId.contains("placeholder"))
            assertFalse(manifest.userId.contains("placeholder"))
            assertTrue(manifest.encryption.iv.isNotBlank())
        }

    @Test
    fun `e2e encryption and restore uses header iv and preserves data integrity`() =
        runBlocking {
            val backupPath = "/backup.enc".toPath()
            val recoveryPhrase =
                listOf("witch", "collapse", "practice", "feed", "shame", "open", "despair", "creek", "road", "again", "ice", "least")
            val secretData = "This is the user's soul. It must be protected at all costs."

            // 1. Create Encrypted Backup
            val progress =
                createBackup(backupPath, recoveryPhrase) { sink ->
                    sink.writeUtf8(secretData)
                }.toList()

            val lastState = progress.last()
            assertTrue("Expected Completed state, got $lastState", lastState is BackupProgress.Completed)
            assertTrue("Encrypted file should exist on disk", fileSystem.exists(backupPath))

            val restored = restoreBackup.readPlaintextForTest(backupPath, recoveryPhrase)

            assertEquals("Decrypted plaintext must match the original secret", secretData, restored.readUtf8())

            // 3. Verify Integrity: Corrupt the file
            val fileBytes = fileSystem.read(backupPath) { readByteArray() }
            // Flip one bit in the middle of the payload/tag
            fileBytes[fileBytes.size - 1] = (fileBytes[fileBytes.size - 1].toInt() xor 0xFF).toByte()
            fileSystem.write(backupPath) { write(fileBytes) }

            try {
                restoreBackup.readPlaintextForTest(backupPath, recoveryPhrase).readUtf8()
                fail("Decryption should have failed due to bit-level corruption (GCM tag mismatch)")
            } catch (_: Exception) {
                // Expected - Authenticated encryption detected the tampering
            }
        }

    @Test
    fun `default encrypted backup writes structured export payload with all note media types`() =
        runBlocking {
            val backupPath = "/structured-backup.enc".toPath()
            val recoveryPhrase =
                listOf("witch", "collapse", "practice", "feed", "shame", "open", "despair", "creek", "road", "again", "ice", "least")
            val exportUserDataUseCase = mockk<ExportUserDataUseCase>()
            val mediaManager =
                FakeBackupMediaManager(
                    readableMedia =
                        mapOf(
                            "file:///local/image-1.jpg" to MediaPayload("image-1.jpg", "image/jpeg", 3, byteArrayOf(1, 2, 3)),
                            "file:///local/video-1.mp4" to MediaPayload("video-1.mp4", "video/mp4", 4, byteArrayOf(4, 5, 6, 7)),
                            "file:///local/audio-1.m4a" to MediaPayload("audio-1.m4a", "audio/mp4", 2, byteArrayOf(8, 9)),
                        ),
                )
            every { exportUserDataUseCase.exportUserData() } returns
                flowOf(ExportProgress.Completed(createExportResult()))
            val createStructuredBackup =
                CreateEncryptedBackupUseCase(
                    exportUserDataUseCase = exportUserDataUseCase,
                    cryptoManager = cryptoManager,
                    fileSystem = fileSystem,
                    mediaManager = mediaManager,
                    deviceIdProvider = { "test-device" },
                    userIdProvider = { "test-user" },
                )

            val progress = createStructuredBackup(backupPath, recoveryPhrase).toList()

            assertTrue(progress.last() is BackupProgress.Completed)
            val plaintext = restoreBackup.readPlaintextForTest(backupPath, recoveryPhrase).readUtf8()
            val payload = json.decodeFromString<EncryptedBackupPayload>(plaintext)
            assertEquals(metadata.serializeForTest(), payload.metadataJson)
            assertTrue(payload.notesJson.contains("\"type\":\"text\""))
            assertTrue(payload.notesJson.contains("\"type\":\"image\""))
            assertTrue(payload.notesJson.contains("\"type\":\"video\""))
            assertTrue(payload.notesJson.contains("\"type\":\"audio\""))
            assertTrue(payload.mediaManifestJson?.contains("media/image-1.jpg") == true)
            assertTrue(payload.mediaManifestJson?.contains("media/video-1.mp4") == true)
            assertTrue(payload.mediaManifestJson?.contains("media/audio-1.m4a") == true)
            assertEquals(3, payload.mediaFiles.size)
            assertEquals(byteArrayOf(1, 2, 3).toList(), payload.mediaFiles[0].decodeBytes().toList())
            assertEquals(byteArrayOf(4, 5, 6, 7).toList(), payload.mediaFiles[1].decodeBytes().toList())
            assertEquals(byteArrayOf(8, 9).toList(), payload.mediaFiles[2].decodeBytes().toList())
        }

    @Test
    fun `encrypted restore imports decrypted backup payload through restore use case`() =
        runBlocking {
            val backupPath = "/restore-payload.enc".toPath()
            val recoveryPhrase =
                listOf("witch", "collapse", "practice", "feed", "shame", "open", "despair", "creek", "road", "again", "ice", "least")
            val restoreUserDataUseCase = mockk<RestoreUserDataUseCase>()
            val bundleSlot = slot<RestoreBundle>()
            val mediaImporterSlot = slot<MediaImporter>()
            val mediaManager = FakeBackupMediaManager()
            coEvery {
                restoreUserDataUseCase.restore(capture(bundleSlot), any(), capture(mediaImporterSlot), any())
            } returns
                RestoreResult(
                    metadata = metadata,
                    journalsImported = 0,
                    notesImported = 4,
                    draftsImported = 0,
                    journalLinksImported = 0,
                    mediaImported = 0,
                    warnings = emptyList(),
                )
            val restoreStructuredBackup =
                RestoreFromEncryptedBackupUseCase(
                    cryptoManager = cryptoManager,
                    fileSystem = fileSystem,
                    restoreUserDataUseCase = restoreUserDataUseCase,
                    mediaManager = mediaManager,
                )
            val payload = createEncryptedBackupPayload()
            createBackup(backupPath, recoveryPhrase) { sink ->
                sink.writeUtf8(json.encodeToString(payload))
            }.toList()

            val progress = restoreStructuredBackup(backupPath, recoveryPhrase).toList()

            assertTrue(progress.contains(RestoreProgress.RestoringData))
            assertTrue(progress.last() is RestoreProgress.Completed)
            coVerify(exactly = 1) {
                restoreUserDataUseCase.restore(any(), any(), any(), any())
            }
            assertEquals(payload.metadataJson, bundleSlot.captured.metadataJson)
            assertEquals(payload.journalsJson, bundleSlot.captured.journalsJson)
            assertTrue(bundleSlot.captured.notesJson.contains("\"type\":\"text\""))
            assertTrue(bundleSlot.captured.notesJson.contains("\"type\":\"image\""))
            assertTrue(bundleSlot.captured.notesJson.contains("\"type\":\"video\""))
            assertTrue(bundleSlot.captured.notesJson.contains("\"type\":\"audio\""))
            val mediaImporter = mediaImporterSlot.captured
            val restoredUri = mediaImporter.importMedia("media/image-1.jpg")
            val restoredBytes =
                mediaManager.savedMedia
                    .single()
                    .data
                    .toList()
            assertEquals("restored://image-1.jpg", restoredUri)
            assertEquals(
                byteArrayOf(1, 2, 3).toList(),
                restoredBytes,
            )
        }

    private fun createEncryptedBackupPayload(): EncryptedBackupPayload =
        createExportResult().let { result ->
            EncryptedBackupPayload(
                metadataJson = result.serializeMetadata(),
                journalsJson = result.serializeJournals(),
                notesJson = result.serializeNotes(),
                journalNotesJson = result.serializeJournalNotes(),
                draftsJson = result.serializeDrafts(),
                profileJson = result.serializeProfile(),
                placesJson = result.serializePlaces(),
                locationHistoryJson = result.serializeLocationHistory(),
                mediaManifestJson = result.serializeMediaManifest(),
                mediaFiles =
                    listOf(
                        EncryptedBackupMediaFile(
                            exportPath = "media/image-1.jpg",
                            fileName = "image-1.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = 3,
                            base64Data = Base64.encode(byteArrayOf(1, 2, 3)),
                        ),
                    ),
            )
        }

    private fun createExportResult(): ExportResult {
        val notes =
            listOf(
                ExportNote(
                    id = "00000000-0000-0000-0000-000000000001",
                    type = "text",
                    content = "Today was worth keeping.",
                    createdAt = Instant.parse("2026-06-14T10:00:00Z"),
                    updatedAt = Instant.parse("2026-06-14T10:01:00Z"),
                ),
                ExportNote(
                    id = "00000000-0000-0000-0000-000000000002",
                    type = "image",
                    mediaPath = "file:///local/image-1.jpg",
                    caption = "Window light",
                    createdAt = Instant.parse("2026-06-14T10:02:00Z"),
                    updatedAt = Instant.parse("2026-06-14T10:03:00Z"),
                ),
                ExportNote(
                    id = "00000000-0000-0000-0000-000000000003",
                    type = "video",
                    mediaPath = "file:///local/video-1.mp4",
                    caption = "Walkthrough",
                    createdAt = Instant.parse("2026-06-14T10:04:00Z"),
                    updatedAt = Instant.parse("2026-06-14T10:05:00Z"),
                ),
                ExportNote(
                    id = "00000000-0000-0000-0000-000000000004",
                    type = "audio",
                    mediaPath = "file:///local/audio-1.m4a",
                    durationMs = 42_000,
                    createdAt = Instant.parse("2026-06-14T10:06:00Z"),
                    updatedAt = Instant.parse("2026-06-14T10:07:00Z"),
                ),
            )
        return ExportResult(
            json = json,
            exportMetadata = metadata,
            journals = emptyList(),
            exportNotes = notes,
            exportRelations = emptyList(),
            exportDrafts = emptyList(),
            profilePayload = null,
            placesPayload = null,
            locationHistoryPayload = null,
            mediaFiles =
                listOf(
                    ExportMediaFile("media/image-1.jpg", "file:///local/image-1.jpg"),
                    ExportMediaFile("media/video-1.mp4", "file:///local/video-1.mp4"),
                    ExportMediaFile("media/audio-1.m4a", "file:///local/audio-1.m4a"),
                ),
            stats = metadata.stats,
            issues = emptyList(),
        )
    }

    private fun ExportMetadata.serializeForTest(): String = json.encodeToString(this)

    @OptIn(ExperimentalEncodingApi::class)
    private fun EncryptedBackupMediaFile.decodeBytes(): ByteArray = Base64.decode(base64Data)
}

private class InMemorySessionStorage(
    initialSession: UserSession? = null,
) : SessionStorage {
    private val sessionState = MutableStateFlow(initialSession)

    override fun getSession(): UserSession? = sessionState.value

    override fun getSessionFlow(): Flow<UserSession?> = sessionState.asStateFlow()

    override suspend fun hasValidSession(): Boolean = sessionState.value != null

    override fun saveSession(session: UserSession) {
        sessionState.value = session
    }

    override fun clearSession() {
        sessionState.value = null
    }
}

private class InMemorySecureStorage : SecureStorage {
    private val values = mutableMapOf<String, String>()
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun getString(key: String): String? = values[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
        state.value = values.toMap()
    }

    override suspend fun remove(key: String) {
        values.remove(key)
        state.value = values.toMap()
    }

    override suspend fun clear() {
        values.clear()
        state.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = state.map { values -> values[key] }

    override fun observeAll(): Flow<Map<String, String>> = state.asStateFlow()

    override suspend fun encrypt(data: ByteArray): ByteArray = data

    override suspend fun decrypt(data: ByteArray): ByteArray = data
}

private class FakeBackupMediaManager(
    private val readableMedia: Map<String, MediaPayload> = emptyMap(),
) : MediaManager {
    val savedMedia = mutableListOf<MediaPayload>()

    override suspend fun getMedia(uri: String): MediaObject = error("Not used")

    override suspend fun exists(mediaId: String): Boolean = false

    override suspend fun getRecentMedia(limit: Int): kotlinx.coroutines.flow.Flow<List<MediaObject>> = flowOf(emptyList())

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): kotlinx.coroutines.flow.Flow<List<MediaObject>> = flowOf(emptyList())

    override suspend fun addToDefaultCollection(uri: String) = Unit

    override suspend fun readMedia(uri: String): MediaPayload = readableMedia[uri] ?: error("Missing readable media for $uri")

    override suspend fun saveMedia(payload: MediaPayload): String {
        savedMedia += payload
        return "restored://${payload.fileName}"
    }

    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String = error("Not used")
}

/**
 * JVM-based CryptoManager for test environments.
 */
class JvmCryptoManager : CryptoManager {
    private val tagSize = 128

    override suspend fun generateRecoveryPhrase(): List<String> = emptyList()

    override fun validateRecoveryPhrase(phrase: List<String>): Boolean = true

    override suspend fun deriveMasterKey(phrase: List<String>): ByteArray {
        val spec = PBEKeySpec(phrase.joinToString(" ").toCharArray(), "salt".toByteArray(), 1000, 128)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    override fun encryptSink(
        sink: Sink,
        key: ByteArray,
        iv: ByteArray,
    ): Sink {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagSize, iv))
        // Buffer the sink as required by CipherSink
        return CipherSink(sink.buffer(), cipher)
    }

    override fun decryptSource(
        source: Source,
        key: ByteArray,
        iv: ByteArray,
    ): Source {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagSize, iv))
        return CipherSource(source.buffer(), cipher)
    }

    override fun generateRandomBytes(size: Int): ByteArray = ByteArray(size) { it.toByte() }

    override fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    override fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagSize, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    override fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagSize, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}
