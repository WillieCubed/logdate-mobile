package app.logdate.client.domain.backup

import app.logdate.client.device.crypto.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.buffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Restores user data from an encrypted backup file.
 *
 * Decryption happens entirely on-device. The recovery phrase is used to derive the
 * decryption key in memory and is never persisted or transmitted to the cloud.
 *
 * @property cryptoManager Handles AES-GCM decryption and master key derivation.
 * @property fileSystem Interface for reading the encrypted backup file from storage.
 */
class RestoreFromEncryptedBackupUseCase(
    private val cryptoManager: CryptoManager,
    private val fileSystem: FileSystem,
) {
    /**
     * Executes the restore process and streams progress updates.
     *
     * @param backupFile The path to the .enc backup file.
     * @param recoveryPhrase The user's 12-word master secret.
     * @return A [Flow] emitting the current state of the restore process.
     */
    @OptIn(ExperimentalEncodingApi::class)
    operator fun invoke(
        backupFile: Path,
        recoveryPhrase: List<String>,
    ): Flow<RestoreProgress> =
        flow {
            emit(RestoreProgress.Starting)

            try {
                emit(RestoreProgress.DerivingKeys)
                val masterKey = deriveKeys(recoveryPhrase)

                emit(RestoreProgress.Decrypting)
                performRestore(backupFile, masterKey)

                emit(RestoreProgress.Completed)
            } catch (e: Exception) {
                emit(RestoreProgress.Failed(e.message ?: "Restore failed"))
            }
        }

    /**
     * Derives the decryption key from the user's secret phrase.
     */
    private suspend fun deriveKeys(recoveryPhrase: List<String>): ByteArray = cryptoManager.deriveMasterKey(recoveryPhrase)

    /**
     * Connects the encrypted source to the restoration logic and performs integrity checks.
     */
    private fun performRestore(
        backupFile: Path,
        masterKey: ByteArray,
    ) {
        readPlaintext(backupFile, masterKey).close()
    }

    @OptIn(ExperimentalEncodingApi::class)
    internal suspend fun readPlaintextForTest(
        backupFile: Path,
        recoveryPhrase: List<String>,
    ): Buffer = readPlaintext(backupFile, deriveKeys(recoveryPhrase))

    @OptIn(ExperimentalEncodingApi::class)
    private fun readPlaintext(
        backupFile: Path,
        masterKey: ByteArray,
    ): Buffer {
        val fileSource = fileSystem.source(backupFile).buffer()
        val header = EncryptedBackupFileFormat.readHeader(fileSource)
        val iv = Base64.decode(header.manifest.encryption.iv)
        val decryptedSource = cryptoManager.decryptSource(fileSource, masterKey, iv).buffer()

        return Buffer().also { plaintext ->
            try {
                plaintext.writeAll(decryptedSource)
            } finally {
                decryptedSource.close()
            }
        }
    }
}

/**
 * Represents the detailed state of the restore operation.
 */
sealed class RestoreProgress {
    /** The restore process has initiated. */
    data object Starting : RestoreProgress()

    /** Deriving the decryption key from the recovery phrase. */
    data object DerivingKeys : RestoreProgress()

    /** Decrypting the stream and verifying data integrity via the GCM tag. */
    data object Decrypting : RestoreProgress()

    /** Restore completed successfully. Data is verified and available. */
    data object Completed : RestoreProgress()

    /** The operation failed (e.g., wrong phrase or corrupted file). */
    data class Failed(
        val error: String,
    ) : RestoreProgress()
}
