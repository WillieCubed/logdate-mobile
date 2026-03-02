package app.logdate.client.domain.backup

import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.shared.model.backup.BackupEncryptionMetadata
import app.logdate.shared.model.backup.BackupManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Orchestrates the creation of a client-side encrypted backup.
 *
 * This use case handles the flow from key derivation to streaming encryption. By using
 * streaming, it ensures that even very large backups containing extensive media blobs
 * do not exceed the device's memory limits.
 *
 * @property exportUserDataUseCase The underlying service used to gather data for export.
 * @property cryptoManager Provides platform-specific AES-GCM and key derivation primitives.
 * @property fileSystem Interface for writing the encrypted backup file to disk.
 * @property clock Provider for the current system time used in backup metadata.
 */
class CreateEncryptedBackupUseCase(
    private val exportUserDataUseCase: ExportUserDataUseCase,
    private val cryptoManager: CryptoManager,
    private val fileSystem: FileSystem,
    private val clock: Clock = Clock.System,
) {
    /**
     * Executes the backup operation and streams progress updates.
     *
     * @param outputPath The destination path where the backup file will be created.
     * @param recoveryPhrase The user's 12-word master secret used to derive the encryption key.
     * @param contentWriter A callback that receives a [BufferedSink] to write the plaintext data.
     *                      The data is encrypted automatically before it is written to disk.
     * @return A [Flow] emitting the current state of the backup process.
     */
    @OptIn(ExperimentalEncodingApi::class)
    operator fun invoke(
        outputPath: Path,
        recoveryPhrase: List<String>,
        contentWriter: (BufferedSink) -> Unit,
    ): Flow<BackupProgress> =
        flow {
            emit(BackupProgress.Starting)

            try {
                val keys = deriveKeys(recoveryPhrase)
                emit(BackupProgress.DerivingKeys)

                val manifest = createManifest(keys.salt, keys.iv)

                emit(BackupProgress.Encrypting)
                writeEncryptedBackup(outputPath, keys, manifest, contentWriter)

                emit(BackupProgress.Completed(outputPath.toString()))
            } catch (e: Exception) {
                emit(BackupProgress.Failed(e.message ?: "Backup failed"))
            }
        }

    /**
     * Derives the master key and generates initialization parameters for the session.
     */
    private suspend fun deriveKeys(recoveryPhrase: List<String>): BackupKeys {
        val masterKey = cryptoManager.deriveMasterKey(recoveryPhrase)
        val iv = cryptoManager.generateRandomBytes(12)
        val salt = cryptoManager.generateRandomBytes(16)
        return BackupKeys(masterKey, iv, salt)
    }

    /**
     * Generates the unencrypted manifest used to identify the backup and its encryption parameters.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun createManifest(
        salt: ByteArray,
        iv: ByteArray,
    ): BackupManifest =
        BackupManifest(
            timestamp = clock.now(),
            deviceId = "device-id-placeholder",
            userId = "user-id-placeholder",
            encryption =
                BackupEncryptionMetadata(
                    salt = Base64.encode(salt),
                    iv = Base64.encode(iv),
                ),
        )

    /**
     * Configures the encrypted stream and executes the data writing process.
     */
    private fun writeEncryptedBackup(
        outputPath: Path,
        keys: BackupKeys,
        manifest: BackupManifest,
        contentWriter: (BufferedSink) -> Unit,
    ) {
        val fileSink = fileSystem.sink(outputPath)
        val encryptedSink = cryptoManager.encryptSink(fileSink, keys.masterKey, keys.iv)
        val bufferedSink = encryptedSink.buffer()

        contentWriter(bufferedSink)

        // Closing the buffer flushes the cipher and appends the GCM authentication tag.
        bufferedSink.close()
    }

    /**
     * Data class holding the derived master key and required salts for a backup session.
     */
    private data class BackupKeys(
        val masterKey: ByteArray,
        val iv: ByteArray,
        val salt: ByteArray,
    )
}

/**
 * Represents the detailed state of the backup operation.
 */
sealed class BackupProgress {
    /** The backup process has initiated. */
    data object Starting : BackupProgress()

    /** Computing the cryptographic master key from the recovery phrase. */
    data object DerivingKeys : BackupProgress()

    /** Data is being streamed, compressed, and encrypted to disk. */
    data object Encrypting : BackupProgress()

    /** The backup completed successfully and is available at [path]. */
    data class Completed(
        val path: String,
    ) : BackupProgress()

    /** An unrecoverable error occurred during the backup. */
    data class Failed(
        val error: String,
    ) : BackupProgress()
}
