package app.logdate.client.database.encryption

import android.content.Context
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    fun hasBackup(): Boolean = backupFile().exists()

    /**
     * Reads the backup passphrase, or null if no backup exists or it's unreadable.
     */
    fun read(): ByteArray? =
        runCatching {
            val file = backupFile()
            if (!file.exists()) return null
            check(file.length() in 1..128) { "Invalid database recovery key size" }
            val encoded = file.readText().trim()
            if (encoded.isEmpty()) return null
            Base64.decode(encoded)
        }.getOrElse {
            Napier.w("Database recovery key could not be read")
            null
        }

    /**
     * Writes the passphrase to the backup file.
     */
    fun write(passphrase: ByteArray) {
        val file = backupFile()
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.part")
        FileOutputStream(temporary).use {
            it.write(Base64.encode(passphrase).encodeToByteArray())
            it.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
