package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.sync.cloud.InMemorySecureStorage
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals

class StoredMediaRecoveryTest {
    @Test
    fun `the production media cipher decrypts with the recovered identity in the same process`() =
        runTest {
            val storage = InMemorySecureStorage()
            val crypto = DesktopCryptoManager()
            val identity = IdentityKeyManager(storage, crypto)
            identity.setupNewIdentity()
            val provider = MediaPayloadKeyProvider(storage, crypto, identity, KeyDerivation(crypto))
            val media = StoredMediaPayloadCrypto(provider)
            val plaintext = "Original encrypted media".encodeToByteArray()
            assertContentEquals(plaintext, media.decrypt(legacyLdce1Encrypt(provider.getOrCreateKey(), plaintext)))
            val recoveredPhrase = crypto.generateRecoveryPhrase()
            identity.recoverIdentity(recoveredPhrase)
            val ciphertext = legacyLdce1Encrypt(provider.getOrCreateKey(), plaintext)
            assertContentEquals(plaintext, media.decrypt(ciphertext))
        }

    @Test
    fun `new media uploads use the recovered key without a process restart`() =
        runTest {
            val storage = InMemorySecureStorage()
            val crypto = DesktopCryptoManager()
            val identity = IdentityKeyManager(storage, crypto)
            identity.setupNewIdentity()
            val provider = MediaPayloadKeyProvider(storage, crypto, identity, KeyDerivation(crypto))
            val media = StoredMediaPayloadCrypto(provider)
            media.streamEncryptor()
            identity.recoverIdentity(crypto.generateRecoveryPhrase())
            val plaintext = "New replacement-phone media".encodeToByteArray()
            val encrypted =
                media
                    .streamEncryptor()
                    .encrypt(Buffer().apply { write(plaintext) })
                    .buffered()
                    .use { it.readByteArray() }
            assertContentEquals(plaintext, AesGcmMediaPayloadCrypto(provider.getOrCreateKey()).decrypt(encrypted))
        }
}
