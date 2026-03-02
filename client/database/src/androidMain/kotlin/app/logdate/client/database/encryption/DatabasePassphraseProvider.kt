package app.logdate.client.database.encryption

import app.logdate.client.device.storage.SecureStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Loads or generates the database encryption passphrase stored in SecureStorage.
 */
@OptIn(ExperimentalEncodingApi::class)
class DatabasePassphraseProvider(
    private val secureStorage: SecureStorage,
) {
    suspend fun getOrCreatePassphrase(): ByteArray {
        val existing = secureStorage.getString(DB_KEY_STORAGE_KEY)
        if (!existing.isNullOrBlank()) {
            return Base64.decode(existing)
        }

        val generated = generateDatabaseKey(KEY_LENGTH_BYTES)
        secureStorage.putString(DB_KEY_STORAGE_KEY, Base64.encode(generated))
        return generated
    }

    suspend fun clearPassphrase() {
        secureStorage.remove(DB_KEY_STORAGE_KEY)
    }

    private companion object {
        const val DB_KEY_STORAGE_KEY = "db_encryption_key"
        const val KEY_LENGTH_BYTES = 32
    }
}
