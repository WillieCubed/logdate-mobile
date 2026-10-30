package app.logdate.client.device.storage

import java.nio.file.Path

/**
 * Encrypts and decrypts whole files with the device key, streaming them instead of reading them
 * into memory. Each call either completes or throws; a failure never writes a partial result as if
 * it were complete.
 */
interface DesktopFileCipher {
    fun encryptFile(
        source: Path,
        target: Path,
    )

    fun decryptFile(
        source: Path,
        target: Path,
    )
}
