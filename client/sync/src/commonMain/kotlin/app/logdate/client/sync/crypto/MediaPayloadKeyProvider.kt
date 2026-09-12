package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes
import io.github.aakira.napier.Napier

/**
 * Supplies the key media payloads are encrypted with.
 *
 * The key is derived from this device's identity key, so the same recovery phrase reproduces it
 * anywhere. That is what lets media uploaded on one device be opened on another, and what lets it
 * survive a reinstall. Earlier builds generated a random key per device instead, which quietly made
 * media readable only on the device that uploaded it; those keys are kept and still used where they
 * exist, because the media already encrypted under them cannot be read any other way.
 */
class MediaPayloadKeyProvider(
    private val secureStorage: SecureStorage,
    private val cryptoManager: CryptoManager,
    private val identityKeyManager: IdentityKeyManager,
    private val keyDerivation: KeyDerivation,
) {
    suspend fun getOrCreateKey(): ByteArray {
        val existing = secureStorage.getBytes(KEY_STORAGE_KEY)
        if (existing != null && existing.size == KEY_LENGTH_BYTES) {
            return existing
        }
        if (existing != null) {
            // A stored key of the wrong length cannot decrypt anything that was encrypted with the
            // real one. Replacing it silently is how key loss turns into unreadable media with no
            // error, so say so before moving on.
            Napier.e("Stored media key is ${existing.size} bytes, not $KEY_LENGTH_BYTES; deriving a fresh one")
        }

        identityKeyManager.ensureIdentityKey()
        val derived =
            keyDerivation.deriveKey(
                identityKey = identityKeyManager.getIdentityKey(),
                context = CONTEXT_MEDIA,
                contentId = MEDIA_KEY_ID,
            )
        secureStorage.putBytes(KEY_STORAGE_KEY, derived)
        return derived
    }

    private companion object {
        const val KEY_STORAGE_KEY = "media_payload_key_v1"
        const val KEY_LENGTH_BYTES = 32
        const val CONTEXT_MEDIA = "media_payload"

        /** Fixed so every device reproduces one media key from the same identity. */
        const val MEDIA_KEY_ID = "media_payload_v1"
    }
}
