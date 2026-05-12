package app.logdate.client.domain.backup

import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.shared.model.backup.BackupManifest
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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
