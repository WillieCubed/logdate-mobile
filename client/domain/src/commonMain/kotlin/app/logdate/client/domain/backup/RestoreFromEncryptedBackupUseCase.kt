package app.logdate.client.domain.backup

import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreResult
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
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
    private val restoreUserDataUseCase: RestoreUserDataUseCase? = null,
    private val mediaManager: MediaManager? = null,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

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
                restoreWithKey(
                    backupFile = backupFile,
                    masterKey = masterKey,
                    onRestoringData = { emit(RestoreProgress.RestoringData) },
                )

                emit(RestoreProgress.Completed)
            } catch (e: Exception) {
                emit(RestoreProgress.Failed(e.message ?: "Restore failed"))
            }
        }

    suspend fun restore(
        backupFile: Path,
        recoveryPhrase: List<String>,
        onProgress: (suspend (RestoreProgress) -> Unit)? = null,
    ): RestoreResult? {
        onProgress?.invoke(RestoreProgress.Starting)
        val masterKey = deriveKeys(recoveryPhrase)
        onProgress?.invoke(RestoreProgress.DerivingKeys)
        onProgress?.invoke(RestoreProgress.Decrypting)
        val result =
            restoreWithKey(
                backupFile = backupFile,
                masterKey = masterKey,
                onRestoringData = { onProgress?.invoke(RestoreProgress.RestoringData) },
            )
        onProgress?.invoke(RestoreProgress.Completed)
        return result
    }

    /**
     * Derives the decryption key from the user's secret phrase.
     */
    private suspend fun deriveKeys(recoveryPhrase: List<String>): ByteArray = cryptoManager.deriveMasterKey(recoveryPhrase)

    /**
     * Connects the encrypted source to the restoration logic and performs integrity checks.
     */
    private suspend fun restoreWithKey(
        backupFile: Path,
        masterKey: ByteArray,
        onRestoringData: suspend () -> Unit,
    ): RestoreResult? {
        val plaintext = readPlaintext(backupFile, masterKey)
        if (restoreUserDataUseCase == null) {
            plaintext.close()
            return null
        }

        onRestoringData()
        val payload = json.decodeFromString<EncryptedBackupPayload>(plaintext.readUtf8())
        val mediaImporter = payload.toMediaImporter()
        return restoreUserDataUseCase.restore(
            bundle = payload.toRestoreBundle(),
            mediaImporter = mediaImporter,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun EncryptedBackupPayload.toMediaImporter(): MediaImporter? {
        if (mediaFiles.isEmpty()) {
            return null
        }
        val manager =
            mediaManager
                ?: throw IllegalStateException("Encrypted backup contains media but no media manager is available.")
        val mediaByPath = mediaFiles.associateBy { it.exportPath.trimStart('/') }
        return object : MediaImporter {
            override suspend fun importMedia(exportPath: String): String? {
                val media = mediaByPath[exportPath.trimStart('/')] ?: return null
                val data = Base64.decode(media.base64Data)
                return manager.saveMedia(
                    MediaPayload(
                        fileName = media.fileName,
                        mimeType = media.mimeType,
                        sizeBytes = media.sizeBytes,
                        data = data,
                    ),
                )
            }
        }
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

    /** Importing the decrypted backup payload into local app data. */
    data object RestoringData : RestoreProgress()

    /** Restore completed successfully. Data is verified and available. */
    data object Completed : RestoreProgress()

    /** The operation failed (e.g., wrong phrase or corrupted file). */
    data class Failed(
        val error: String,
    ) : RestoreProgress()
}
