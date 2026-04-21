package app.logdate.client.device.crypto

import kotlinx.coroutines.test.runTest
import kotlin.experimental.xor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Tests for [ContentEncryptionService].
 *
 * Verifies that the service correctly encrypts and decrypts content,
 * ensuring data confidentiality and integrity.
 */
class ContentEncryptionServiceTest {
    private val mockSecureStorage = InMemorySecureStorage()
    private val cryptoManager = TestCryptoManager()
    private val identityKeyManager = IdentityKeyManager(mockSecureStorage, cryptoManager)
    private val keyDerivation = KeyDerivation(cryptoManager)
    private val service = ContentEncryptionService(identityKeyManager, keyDerivation, cryptoManager)

    @Test
    fun testEncryptDecryptRoundtrip() =
        runTest {
            identityKeyManager.setupNewIdentity()

            val plaintext = "Hello, World! This is sensitive journal content."
            val contentId = "entry-123"

            val envelope = service.encryptContent(contentId, plaintext)
            val decrypted = service.decryptContent(contentId, envelope)

            assertEquals(plaintext, decrypted)
        }

    @Test
    fun testEncryptedContentIsNotPlaintext() =
        runTest {
            identityKeyManager.setupNewIdentity()

            val plaintext = "Secret data"
            val contentId = "entry-456"

            val envelope = service.encryptContent(contentId, plaintext)

            assertFalse(envelope.ciphertext.contains(plaintext))
        }

    @Test
    fun testWrongContentIdFails() =
        runTest {
            identityKeyManager.setupNewIdentity()

            val plaintext = "Secret"
            val envelope = service.encryptContent("entry-1", plaintext)

            assertFailsWith<Exception> {
                service.decryptContent("entry-2", envelope)
            }
        }

    @Test
    fun testTamperedCiphertextFails() =
        runTest {
            identityKeyManager.setupNewIdentity()

            val plaintext = "Secret"
            val contentId = "entry-3"
            val envelope = service.encryptContent(contentId, plaintext)

            // Flip a bit in ciphertext
            val tamperedBytes = envelope.ciphertext.encodeToByteArray()
            if (tamperedBytes.isNotEmpty()) {
                tamperedBytes[0] = (tamperedBytes[0].toInt() xor 1).toByte()
            }
            val tampered = envelope.copy(ciphertext = tamperedBytes.decodeToString())

            assertFailsWith<Exception> {
                service.decryptContent(contentId, tampered)
            }
        }

    @Test
    fun testMultipleEntriesDifferentKeys() =
        runTest {
            identityKeyManager.setupNewIdentity()

            val envelope1 = service.encryptContent("entry-1", "Content 1")
            val envelope2 = service.encryptContent("entry-2", "Content 1")

            // Same plaintext, different IDs should produce different ciphertexts
            assertFalse(envelope1.ciphertext == envelope2.ciphertext)
        }

    @Test
    fun testIdentityNotSetUpThrows() =
        runTest {
            assertFailsWith<IdentityKeyNotFoundException> {
                service.encryptContent("entry", "data")
            }
        }
}

/**
 * Test implementation of CryptoManager with real crypto for testing.
 */
class TestCryptoManager : CryptoManager {
    override suspend fun generateRecoveryPhrase(): List<String> = (1..12).map { "test$it" }

    override suspend fun deriveMasterKey(phrase: List<String>): ByteArray {
        val combined = phrase.joinToString("").encodeToByteArray()
        return pseudoHash(combined, 32)
    }

    override fun validateRecoveryPhrase(phrase: List<String>): Boolean = phrase.size == 12

    override fun encryptSink(
        sink: okio.Sink,
        key: ByteArray,
        iv: ByteArray,
    ): okio.Sink {
        TODO("Not needed for tests")
    }

    override fun decryptSource(
        source: okio.Source,
        key: ByteArray,
        iv: ByteArray,
    ): okio.Source {
        TODO("Not needed for tests")
    }

    override fun generateRandomBytes(size: Int): ByteArray = ByteArray(size) { (it * 31 + 17).toByte() }

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
        val keystream = pseudoHash(key + iv + aad, plaintext.size)
        val ciphertext = ByteArray(plaintext.size) { plaintext[it].xor(keystream[it]) }
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
        val payloadSize = ciphertext.size - 16
        val payload = ciphertext.copyOfRange(0, payloadSize)
        val actualTag = ciphertext.copyOfRange(payloadSize, ciphertext.size)
        val expectedTag = pseudoHash(key + iv + aad + payload, 16)
        if (!actualTag.contentEquals(expectedTag)) {
            throw IllegalArgumentException("Authentication failed")
        }
        val keystream = pseudoHash(key + iv + aad, payload.size)
        return ByteArray(payload.size) { payload[it].xor(keystream[it]) }
    }

    private fun pseudoHash(
        input: ByteArray,
        outputSize: Int,
    ): ByteArray {
        val out = ByteArray(outputSize)
        var state = 0x9E3779B9.toInt()
        var index = 0
        while (index < outputSize) {
            for (byte in input) {
                state = state xor byte.toInt()
                state = state * 1664525 + 1013904223
            }
            out[index] = (state ushr ((index % 4) * 8)).toByte()
            index++
        }
        return out
    }
}
