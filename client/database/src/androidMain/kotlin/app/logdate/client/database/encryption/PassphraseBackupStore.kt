package app.logdate.client.database.encryption

import android.content.Context
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileInputStream
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
    fun hasBackup(): Boolean = synchronized(backupLock) { backupFile().exists() || pendingFile().exists() }

    /**
     * Reads the backup passphrase, or null if no backup exists or it's unreadable.
     */
    fun read(): ByteArray? = synchronized(backupLock) { readKey(backupFile()) }

    /** Interrupted writes are candidates, never replacements for the committed key. */
    fun readCandidates(): List<ByteArray> =
        synchronized(backupLock) {
            listOfNotNull(readKey(backupFile()), readKey(pendingFile()))
        }

    private fun readKey(file: File): ByteArray? =
        runCatching {
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
        require(passphrase.size == 32) { "Database passphrase has an invalid length" }
        synchronized(backupLock) {
            val file = backupFile()
            file.parentFile?.mkdirs()
            val temporary = pendingFile()
            // The pending file can be the only surviving key. Promote it without opening a
            // truncating output stream when SQLCipher has selected that same key.
            if (readKey(temporary)?.contentEquals(passphrase) != true) {
                FileOutputStream(temporary).use {
                    it.write(Base64.encode(passphrase).encodeToByteArray())
                    it.fd.sync()
                }
            } else {
                FileInputStream(temporary).use { it.fd.sync() }
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Deletes the backup file.
     */
    fun clear() {
        synchronized(backupLock) {
            runCatching {
                listOf(backupFile(), pendingFile()).forEach { file ->
                    check(!file.exists() || file.delete()) { "Unable to remove database recovery material" }
                }
            }.onFailure { error ->
                Napier.w("Failed to clear passphrase backup", error)
            }
        }
    }

    private fun backupFile(): File = File(context.filesDir, BACKUP_PATH)

    private fun pendingFile(): File = File(context.filesDir, "$BACKUP_PATH.part")

    private companion object {
        const val BACKUP_PATH = "db_recovery/passphrase_backup"
        val backupLock = Any()
    }
}
