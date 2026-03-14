package app.logdate.client.database.encryption

import android.content.Context
import io.github.aakira.napier.Napier
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Stores a recoverable backup of the database passphrase in a file within
 * [Context.getFilesDir]. Unlike [app.logdate.client.device.storage.SecureStorage]
 * (which is KeyStore-bound and lost when the device's KeyStore changes), this
 * backup file survives device-to-device transfers and cloud restores.
 *
 * ## Security model
 *
 * The backup file is protected by:
 * - Linux file permissions (readable only by the app process)
 * - Android file-based encryption (FBE) at rest
 * - Google's client-side encryption in cloud backups
 *
 * It is NOT protected by a hardware-backed KeyStore key, which is a deliberate
 * trade-off: losing the hardware-backed defense-in-depth is preferable to
 * irrecoverable data loss when the KeyStore doesn't survive a device transfer.
 */
@OptIn(ExperimentalEncodingApi::class)
class PassphraseBackupStore(
    private val context: Context,
) {
    /**
     * Reads the backup passphrase, or null if no backup exists or it's unreadable.
     */
    fun read(): ByteArray? =
        runCatching {
            val file = backupFile()
            if (!file.exists()) return null
            val encoded = file.readText().trim()
            if (encoded.isEmpty()) return null
            Base64.decode(encoded)
        }.getOrElse { error ->
            Napier.w("Failed to read passphrase backup", error)
            null
        }

    /**
     * Writes the passphrase to the backup file.
     */
    fun write(passphrase: ByteArray) {
        runCatching {
            val file = backupFile()
            file.parentFile?.mkdirs()
            file.writeText(Base64.encode(passphrase))
        }.onFailure { error ->
            Napier.w("Failed to write passphrase backup", error)
        }
    }

    /**
     * Deletes the backup file.
     */
    fun clear() {
        runCatching {
            val file = backupFile()
            if (file.exists()) {
                file.delete()
            }
        }.onFailure { error ->
            Napier.w("Failed to clear passphrase backup", error)
        }
    }

    private fun backupFile(): File = File(context.filesDir, BACKUP_PATH)

    private companion object {
        const val BACKUP_PATH = "db_recovery/passphrase_backup"
    }
}
