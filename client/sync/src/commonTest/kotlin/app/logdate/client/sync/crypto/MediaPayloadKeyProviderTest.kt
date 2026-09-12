package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.putBytes
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.TestCryptoManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Media used to be encrypted with a key generated at random on whichever device uploaded it, so it
 * could never be opened anywhere else and vanished for good when the app was reinstalled. Deriving
 * the key from the identity key makes it reproducible from the recovery phrase instead.
 */
class MediaPayloadKeyProviderTest {
    private fun provider(
        storage: SecureStorage,
        identityKeyManager: IdentityKeyManager,
        cryptoManager: TestCryptoManager,
    ) = MediaPayloadKeyProvider(storage, cryptoManager, identityKeyManager, KeyDerivation(cryptoManager))

    @Test
    fun `the same recovery phrase yields the same media key on another device`() =
        runTest {
            val cryptoManager = TestCryptoManager()
            val firstStorage = InMemorySecureStorage()
            val firstIdentity = IdentityKeyManager(firstStorage, cryptoManager)
            val phrase = firstIdentity.setupNewIdentity()
            val firstKey = provider(firstStorage, firstIdentity, cryptoManager).getOrCreateKey()

            val secondStorage = InMemorySecureStorage()
            val secondIdentity = IdentityKeyManager(secondStorage, cryptoManager)
            secondIdentity.recoverIdentity(phrase.words)
            val secondKey = provider(secondStorage, secondIdentity, cryptoManager).getOrCreateKey()

            assertTrue(
                firstKey.contentEquals(secondKey),
                "Media encrypted on one device must be readable on another that recovered the same identity",
            )
        }

    @Test
    fun `a key stored by an older build is kept`() =
        runTest {
            // Media already encrypted with a random per-device key cannot be read any other way, so
            // replacing it with a derived key would make that media permanently unopenable.
            val cryptoManager = TestCryptoManager()
            val storage = InMemorySecureStorage()
            val identity = IdentityKeyManager(storage, cryptoManager)
            identity.setupNewIdentity()
            val legacyKey = ByteArray(32) { index -> (index + 7).toByte() }
            storage.putBytes("media_payload_key_v1", legacyKey)

            val key = provider(storage, identity, cryptoManager).getOrCreateKey()

            assertTrue(key.contentEquals(legacyKey), "An existing media key must be preserved")
        }

    @Test
    fun `a device with no identity still gets a reproducible key`() =
        runTest {
            val cryptoManager = TestCryptoManager()
            val storage = InMemorySecureStorage()
            val identity = IdentityKeyManager(storage, cryptoManager)

            val key = provider(storage, identity, cryptoManager).getOrCreateKey()

            assertTrue(identity.hasIdentityKey(), "Provisioning the media key must establish an identity to derive from")
            assertFalse(key.all { it == 0.toByte() })
        }
}
