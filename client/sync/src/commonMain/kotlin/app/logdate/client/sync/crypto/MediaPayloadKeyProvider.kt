package app.logdate.client.sync.crypto

import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes

class MediaPayloadKeyProvider(
    private val secureStorage: SecureStorage,
    private val cryptoManager: CryptoManager
) {
    suspend fun getOrCreateKey(): ByteArray {
        val existing = secureStorage.getBytes(KEY_STORAGE_KEY)
        if (existing != null && existing.size == KEY_LENGTH_BYTES) {
            return existing
        }

        val generated = cryptoManager.generateRandomBytes(KEY_LENGTH_BYTES)
        secureStorage.putBytes(KEY_STORAGE_KEY, generated)
        return generated
    }

    private companion object {
        const val KEY_STORAGE_KEY = "media_payload_key_v1"
        const val KEY_LENGTH_BYTES = 32
    }
}
