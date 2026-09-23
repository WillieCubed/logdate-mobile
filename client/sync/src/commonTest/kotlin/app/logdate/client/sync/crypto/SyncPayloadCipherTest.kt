package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A wrong-key payload and a corrupted one both fail AES-GCM the same way, which made every
 * unreadable synced value look like data loss even when it was really just made on a device with
 * a different identity key. The LDSE2 envelope carries a fingerprint of the key that wrote it, so
 * a mismatch is recognized before decryption is even attempted.
 */
class SyncPayloadCipherTest {
    @Test
    fun `a value round-trips through the current envelope version`() =
        runTest {
            val cipher = cipherFor("seed")

            val encrypted = cipher.encryptString("field", "hello world")

            assertEquals("hello world", cipher.decryptString("field", encrypted))
        }

    @Test
    fun `encrypting always writes the current envelope version`() =
        runTest {
            val cipher = cipherFor("seed")

            val encrypted = cipher.encryptString("field", "hello world")

            assertEquals(true, encrypted.startsWith("LDSE2:"), "New writes should use the current envelope, not the legacy one")
        }

    @Test
    fun `a value made with a different identity key is unreadable not silently wrong`() =
        runTest {
            val writer = cipherFor("device-a")
            val reader = cipherFor("device-b")
            val encrypted = writer.encryptString("field", "hello world")

            assertFailsWith<UnreadablePayloadException> { reader.decryptString("field", encrypted) }
        }

    @Test
    fun `a legacy v1 envelope from before fingerprinting still decrypts`() =
        runTest {
            val cryptoManager = TestCryptoManager()
            val identityKeyManager = IdentityKeyManager(InMemorySecureStorage(), cryptoManager)
            identityKeyManager.recoverIdentity((1..12).map { "legacy-$it" })
            val contentEncryptionService = ContentEncryptionService(identityKeyManager, KeyDerivation(cryptoManager), cryptoManager)
            val envelope = contentEncryptionService.encryptContent("field", "hello world")
            val legacyPayload = "LDSE1:" + Json.encodeToString(envelope)
            val cipher = SyncPayloadCipher(contentEncryptionService, identityKeyManager, cryptoManager)

            assertEquals("hello world", cipher.decryptString("field", legacyPayload))
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
}
