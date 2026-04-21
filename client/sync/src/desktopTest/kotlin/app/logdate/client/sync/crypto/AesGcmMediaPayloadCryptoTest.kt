package app.logdate.client.sync.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for media payload encryption using AES-GCM.
 *
 * These tests verify the cryptographic operations of [AesGcmMediaPayloadCrypto], ensuring
 * that plaintext media is correctly encrypted with a specific prefix, that already
 * encrypted payloads are handled gracefully, and that decryption fails appropriately
 * when the ciphertext or tag has been tampered with.
 */
class AesGcmMediaPayloadCryptoTest {
    @Test
    fun encryptAddsPrefixAndDecrypts() =
        runTest {
            val key = ByteArray(32) { index -> (index + 1).toByte() }
            val crypto = AesGcmMediaPayloadCrypto(key)
            val plaintext = "hello-media".encodeToByteArray()

            val encrypted = crypto.encrypt(plaintext)

            assertTrue(encrypted.size > plaintext.size)
            val prefix = encrypted.copyOfRange(0, CLIENT_MEDIA_PREFIX_BYTES.size)
            assertTrue(prefix.contentEquals(CLIENT_MEDIA_PREFIX_BYTES))

            val decrypted = crypto.decrypt(encrypted)
            assertTrue(decrypted.contentEquals(plaintext))
        }

    @Test
    fun encryptSkipsAlreadyPrefixedPayloads() =
        runTest {
            val key = ByteArray(32) { index -> (index + 2).toByte() }
            val crypto = AesGcmMediaPayloadCrypto(key)
            val payload = CLIENT_MEDIA_PREFIX_BYTES + ByteArray(32) { index -> (index + 7).toByte() }

            val encrypted = crypto.encrypt(payload)

            assertTrue(encrypted.contentEquals(payload))
        }

    @Test
    fun decryptPassesThroughPlaintext() =
        runTest {
            val key = ByteArray(32) { index -> (index + 3).toByte() }
            val crypto = AesGcmMediaPayloadCrypto(key)
            val plaintext = ByteArray(16) { index -> (index + 9).toByte() }

            val decrypted = crypto.decrypt(plaintext)

            assertEquals(plaintext.size, decrypted.size)
            assertTrue(decrypted.contentEquals(plaintext))
        }

    @Test
    fun decryptFailsOnTamperedCiphertext() =
        runTest {
            val key = ByteArray(32) { index -> (index + 5).toByte() }
            val crypto = AesGcmMediaPayloadCrypto(key)
            val plaintext = "tamper-check".encodeToByteArray()

            val encrypted = crypto.encrypt(plaintext).copyOf()
            encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0xFF).toByte()

            assertFailsWith<Exception> {
                crypto.decrypt(encrypted)
            }
        }
}
