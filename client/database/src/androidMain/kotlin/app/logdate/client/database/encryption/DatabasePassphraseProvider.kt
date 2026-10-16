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
 * Resolution order: valid primary → valid backup → generate only for a new database.
 * After any resolution, both locations are synced.
 */
@OptIn(ExperimentalEncodingApi::class)
class DatabasePassphraseProvider(
    private val secureStorage: SecureStorage,
    private val backupStore: PassphraseBackupStore,
    private val hasExistingDatabase: () -> Boolean = { false },
) {
    suspend fun getOrCreatePassphrase(): ByteArray {
        // 1. Try the primary (hardware-backed) store.
        val primary = secureStorage.getString(DB_KEY_STORAGE_KEY)
        val primaryKey =
            primary?.let { encoded ->
                runCatching { Base64.decode(encoded) }.getOrNull()?.takeIf { it.size == KEY_LENGTH_BYTES }
            }
        if (primaryKey != null) {
            val backup = backupStore.read()?.takeIf { it.size == KEY_LENGTH_BYTES }
            check(!backupStore.hasBackup() || (backup != null && primaryKey.contentEquals(backup))) {
                "The retained journal has conflicting database recovery keys"
            }
            // Ensure the backup is in sync.
            backupStore.write(primaryKey)
            return primaryKey
        }

        // 2. Primary is empty (fresh install or post-restore). Try the backup.
        val backup = backupStore.read()
        if (backup != null && backup.size == KEY_LENGTH_BYTES) {
            Napier.i("Database passphrase recovered from backup store")
            // Re-populate the primary store so subsequent reads are fast.
            secureStorage.putString(DB_KEY_STORAGE_KEY, Base64.encode(backup))
            return backup
        }

        check(!backupStore.hasBackup() && !hasExistingDatabase()) {
            "The retained journal database needs its original encryption key"
        }

        // A replacement key is only meaningful when there is no retained database.
        Napier.i("Generating new database passphrase (no existing key found)")
        val generated = generateDatabaseKey(KEY_LENGTH_BYTES)
        backupStore.write(generated)
        secureStorage.putString(DB_KEY_STORAGE_KEY, Base64.encode(generated))
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
