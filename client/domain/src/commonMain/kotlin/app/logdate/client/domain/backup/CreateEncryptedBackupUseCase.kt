package app.logdate.client.domain.backup

import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.shared.model.backup.BackupEncryptionMetadata
import app.logdate.shared.model.backup.BackupManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
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
    private val mediaManager: MediaManager? = null,
    private val clock: Clock = Clock.System,
    private val deviceIdProvider: () -> String = { "local-device" },
    private val userIdProvider: () -> String = { "local-user" },
) {
    private val json =
        Json {
            encodeDefaults = true
        }

    /**
     * Executes a standard encrypted backup using the app's full user-data export payload.
     */
    @OptIn(ExperimentalEncodingApi::class)
    operator fun invoke(
        outputPath: Path,
        recoveryPhrase: List<String>,
    ): Flow<BackupProgress> =
        flow {
            emit(BackupProgress.Starting)

            try {
                emit(BackupProgress.ExportingData)
                val exportResult =
                    exportUserDataUseCase
                        .exportUserData()
                        .filterIsInstance<ExportProgress.Completed>()
                        .first()
                        .result
                val payload = exportResult.toEncryptedBackupPayload()

                val keys = deriveKeys(recoveryPhrase)
                emit(BackupProgress.DerivingKeys)

                val manifest = createManifest(keys.salt, keys.iv)

                emit(BackupProgress.Encrypting)
                writeEncryptedBackup(outputPath, keys, manifest) { sink ->
                    sink.writeUtf8(json.encodeToString(payload))
                }

                emit(BackupProgress.Completed(outputPath.toString()))
            } catch (e: Exception) {
                emit(BackupProgress.Failed(e.message ?: "Backup failed"))
            }
        }

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
            deviceId = deviceIdProvider().ifBlank { "local-device" },
            userId = userIdProvider().ifBlank { "local-user" },
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
        val fileSink = fileSystem.sink(outputPath).buffer()
        EncryptedBackupFileFormat.writeHeader(fileSink, manifest)
        fileSink.flush()

        val encryptedSink = cryptoManager.encryptSink(fileSink, keys.masterKey, keys.iv)
        val encryptedBufferedSink = encryptedSink.buffer()

        contentWriter(encryptedBufferedSink)

        // Closing the buffer flushes the cipher, appends the GCM authentication tag, and closes the file.
        encryptedBufferedSink.close()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun ExportResult.toEncryptedBackupPayload(): EncryptedBackupPayload =
        EncryptedBackupPayload(
            metadataJson = serializeMetadata(),
            journalsJson = serializeJournals(),
            notesJson = serializeNotes(),
            journalNotesJson = serializeJournalNotes(),
            draftsJson = serializeDrafts(),
            profileJson = serializeProfile(),
            placesJson = serializePlaces(),
            locationHistoryJson = serializeLocationHistory(),
            mediaManifestJson = serializeMediaManifest(),
            mediaFiles = readEncryptedBackupMediaFiles(),
        )

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun ExportResult.readEncryptedBackupMediaFiles(): List<EncryptedBackupMediaFile> =
        mediaFiles.map { mediaFile ->
            val payload = readMediaPayload(mediaFile.sourceUri)
            EncryptedBackupMediaFile(
                exportPath = mediaFile.exportPath,
                fileName = mediaFile.exportPath.substringAfterLast('/').ifBlank { "media" },
                mimeType = payload.mimeType,
                sizeBytes = payload.bytes.size.toLong(),
                base64Data = Base64.encode(payload.bytes),
            )
        }

    private suspend fun readMediaPayload(sourceUri: String): BackupMediaPayload {
        mediaManager?.let { manager ->
            runCatching {
                val media = manager.readMedia(sourceUri)
                return BackupMediaPayload(
                    bytes = media.data,
                    mimeType = media.mimeType,
                )
            }
        }

        val path = sourceUri.toLocalPath()
        val bytes =
            runCatching {
                fileSystem.read(path) { readByteArray() }
            }.getOrElse { error ->
                throw IllegalStateException("Unable to include media in encrypted backup: $sourceUri", error)
            }
        return BackupMediaPayload(
            bytes = bytes,
            mimeType = sourceUri.inferMimeType(),
        )
    }

    private fun String.toLocalPath(): Path =
        when {
            startsWith("file://") -> removePrefix("file://").toPath()
            startsWith("/") -> toPath()
            else -> throw IllegalStateException("Encrypted backup cannot read non-file media URI without MediaManager: $this")
        }

    private fun String.inferMimeType(): String =
        when (substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }

    private data class BackupMediaPayload(
        val bytes: ByteArray,
        val mimeType: String,
    )

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

    /** Collecting app data for the encrypted backup payload. */
    data object ExportingData : BackupProgress()

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
