package app.logdate.client.sync.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for media payload encryption using AES-GCM.
 *
 * These tests verify that [AesGcmMediaPayloadCrypto] reads media in the legacy LDCE1 format,
 * passes unencrypted media through, refuses encryption formats it does not know, and fails
 * when the ciphertext or tag has been tampered with.
 */
class AesGcmMediaPayloadCryptoTest {
    @Test
    fun `decrypts media in the legacy single-shot format`() =
        runTest {
            val key = ByteArray(32) { index -> (index + 1).toByte() }
            val crypto = AesGcmMediaPayloadCrypto(key)
            val plaintext = "hello-media".encodeToByteArray()

            val encrypted = legacyLdce1Encrypt(key, plaintext)

            assertTrue(encrypted.size > plaintext.size)
            val prefix = encrypted.copyOfRange(0, CLIENT_MEDIA_PREFIX_BYTES.size)
            assertTrue(prefix.contentEquals(CLIENT_MEDIA_PREFIX_BYTES))

            val decrypted = crypto.decrypt(encrypted)
            assertTrue(decrypted.contentEquals(plaintext))
        }

    @Test
    fun `decrypt rejects a client encryption format it does not know`() =
        runTest {
            val crypto = AesGcmMediaPayloadCrypto(ByteArray(32) { index -> (index + 2).toByte() })
            val futureFormat = "LDCE9".encodeToByteArray() + ByteArray(64)

            assertFailsWith<IllegalArgumentException> { crypto.decrypt(futureFormat) }
        }

    @Test
    fun `decrypt passes through plaintext`() =
        runTest {
            val key = ByteArray(32) { index -> (index + 3).toByte() }
            val crypto = AesGcmMediaPayloadCrypto(key)
            val plaintext = ByteArray(16) { index -> (index + 9).toByte() }

            val decrypted = crypto.decrypt(plaintext)

            assertEquals(plaintext.size, decrypted.size)
            assertTrue(decrypted.contentEquals(plaintext))
        }

    @Test
    fun `decrypt fails on tampered ciphertext`() =
        runTest {
            val key = ByteArray(32) { index -> (index + 5).toByte() }
            val crypto = AesGcmMediaPayloadCrypto(key)
            val plaintext = "tamper-check".encodeToByteArray()

            val encrypted = legacyLdce1Encrypt(key, plaintext)
            encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0xFF).toByte()

            assertFailsWith<Exception> {
                crypto.decrypt(encrypted)
            }
        }
}
