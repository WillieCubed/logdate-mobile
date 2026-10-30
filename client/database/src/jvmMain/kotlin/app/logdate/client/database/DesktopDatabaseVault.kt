package app.logdate.client.database

import app.logdate.client.device.storage.DesktopFileCipher
import io.github.aakira.napier.Napier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** The encrypted desktop database exists but could not be decrypted, so no database was opened. */
class EncryptedDatabaseUnavailableException(
    path: Path,
    cause: Throwable,
) : IllegalStateException("The encrypted journal database at $path could not be opened. It was left unchanged.", cause)

/**
 * Keeps the desktop database encrypted while the app is closed.
 *
 * At launch, [unseal] decrypts the sealed copy into place. At exit, [seal] encrypts the closed
 * database and only then removes the unencrypted file. Neither step ever replaces data it could not
 * read: a sealed copy that cannot be decrypted stops startup, and a database that cannot be sealed
 * is kept as it is.
 */
class DesktopDatabaseVault(
    private val cipher: DesktopFileCipher,
    private val databasePath: Path,
) {
    private val sealedPath = databasePath.sibling(".enc")

    fun unseal() {
        if (Files.exists(databasePath) || !Files.exists(sealedPath)) return
        Files.createDirectories(databasePath.parent)
        val staging = databasePath.sibling(".unsealing")
        try {
            cipher.decryptFile(sealedPath, staging)
            Files.move(staging, databasePath, StandardCopyOption.ATOMIC_MOVE)
        } catch (failure: Exception) {
            Files.deleteIfExists(staging)
            throw EncryptedDatabaseUnavailableException(sealedPath, failure)
        }
    }

    /** Seals the database. Call only after it is closed, so every committed write is in the main file. */
    fun seal() {
        if (!Files.exists(databasePath)) return
        val pendingLog = databasePath.sibling("-wal")
        if (Files.exists(pendingLog) && Files.size(pendingLog) > 0) {
            Napier.e("The desktop database still has unsaved log pages; it was left unencrypted so they are kept")
            return
        }
        val staging = sealedPath.sibling(".sealing")
        try {
            cipher.encryptFile(databasePath, staging)
            Files.move(staging, sealedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (failure: Exception) {
            Files.deleteIfExists(staging)
            Napier.e("The desktop database could not be encrypted; the unencrypted copy was kept", failure)
            return
        }
        Files.deleteIfExists(pendingLog)
        Files.deleteIfExists(databasePath.sibling("-shm"))
        Files.delete(databasePath)
    }

    private fun Path.sibling(suffix: String): Path = resolveSibling("$fileName$suffix")
}
