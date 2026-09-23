package app.logdate.client.device.crypto

import android.content.Context
import io.github.aakira.napier.Napier
import java.io.File

/**
 * Stores a recoverable backup of the identity recovery phrase in a file within
 * [Context.getFilesDir]. Mirrors [app.logdate.client.database.encryption.PassphraseBackupStore]'s
 * trade-off for the database passphrase: [app.logdate.client.device.storage.SecureStorage]
 * (EncryptedSharedPreferences backed by the Android KeyStore) is lost whenever the device's
 * KeyStore doesn't survive a device-to-device transfer or cloud restore, while this file does --
 * it is included in both, via `data_extraction_rules.xml` and `backup_rules.xml`.
 *
 * The identity key itself is never written here, only the phrase it's deterministically derived
 * from (see [IdentityKeyManager.setupNewIdentity]), so restoring this file is enough to rebuild
 * the key on a new device.
 *
 * ## Security model
 *
 * Protected the same way as the database passphrase backup:
 * - Linux file permissions (readable only by the app process)
 * - Android file-based encryption (FBE) at rest
 * - Google's client-side encryption in cloud backups
 *
 * It is NOT protected by a hardware-backed KeyStore key, which is a deliberate trade-off: losing
 * the hardware-backed defense-in-depth is preferable to irrecoverable data loss when the KeyStore
 * doesn't survive a device transfer.
 */
class AndroidIdentityKeyBackupStore(
    private val context: Context,
) : IdentityKeyBackupStore {
    override suspend fun readPhrase(): String? =
        runCatching {
            val file = backupFile()
            if (!file.exists()) return null
            val phrase = file.readText().trim()
            phrase.ifEmpty { null }
        }.getOrElse { error ->
            Napier.w("Failed to read identity recovery phrase backup", error)
            null
        }

    override suspend fun writePhrase(phrase: String) {
        runCatching {
            val file = backupFile()
            file.parentFile?.mkdirs()
            file.writeText(phrase)
        }.onFailure { error ->
            Napier.w("Failed to write identity recovery phrase backup", error)
        }
    }

    override suspend fun clear() {
        runCatching {
            val file = backupFile()
            if (file.exists()) {
                file.delete()
            }
        }.onFailure { error ->
            Napier.w("Failed to clear identity recovery phrase backup", error)
        }
    }

    private fun backupFile(): File = File(context.filesDir, BACKUP_PATH)

    private companion object {
        const val BACKUP_PATH = "identity_recovery/phrase_backup"
    }
}
