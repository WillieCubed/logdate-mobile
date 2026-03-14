package app.logdate.client.database.encryption

import app.logdate.client.device.storage.SecureStorage
import io.github.aakira.napier.Napier
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Loads or generates the database encryption passphrase.
 *
 * The passphrase is stored in two locations:
 * 1. **Primary**: [SecureStorage] (EncryptedSharedPreferences backed by Android KeyStore).
 *    Hardware-backed, but lost when the KeyStore doesn't survive a device transfer.
 * 2. **Backup**: [PassphraseBackupStore] (a file in `filesDir/db_recovery/`).
 *    Included in both D2D transfers and cloud backups. Lets us recover the
 *    passphrase and open the encrypted database even when the KeyStore is gone.
 *
 * Resolution order: primary → backup → generate new.
 * After any resolution, both locations are synced.
 */
@OptIn(ExperimentalEncodingApi::class)
class DatabasePassphraseProvider(
    private val secureStorage: SecureStorage,
    private val backupStore: PassphraseBackupStore,
) {
    suspend fun getOrCreatePassphrase(): ByteArray {
        // 1. Try the primary (hardware-backed) store.
        val primary = secureStorage.getString(DB_KEY_STORAGE_KEY)
        if (!primary.isNullOrBlank()) {
            val passphrase = Base64.decode(primary)
            // Ensure the backup is in sync.
            backupStore.write(passphrase)
            return passphrase
        }

        // 2. Primary is empty (fresh install or post-restore). Try the backup.
        val backup = backupStore.read()
        if (backup != null && backup.size == KEY_LENGTH_BYTES) {
            Napier.i("Database passphrase recovered from backup store")
            // Re-populate the primary store so subsequent reads are fast.
            secureStorage.putString(DB_KEY_STORAGE_KEY, Base64.encode(backup))
            return backup
        }

        // 3. Neither store has the passphrase — generate a new one.
        Napier.i("Generating new database passphrase (no existing key found)")
        val generated = generateDatabaseKey(KEY_LENGTH_BYTES)
        secureStorage.putString(DB_KEY_STORAGE_KEY, Base64.encode(generated))
        backupStore.write(generated)
        return generated
    }

    suspend fun clearPassphrase() {
        secureStorage.remove(DB_KEY_STORAGE_KEY)
        backupStore.clear()
    }

    private companion object {
        const val DB_KEY_STORAGE_KEY = "db_encryption_key"
        const val KEY_LENGTH_BYTES = 32
    }
}
