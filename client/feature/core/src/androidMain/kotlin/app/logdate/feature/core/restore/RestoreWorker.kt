package app.logdate.feature.core.restore

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.domain.backup.EncryptedBackupFileFormat
import app.logdate.client.domain.backup.RestoreFromEncryptedBackupUseCase
import app.logdate.client.domain.backup.RestoreProgress
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.buffer
import okio.source
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlin.uuid.Uuid

/**
 * WorkManager worker for restoring user data from a LogDate export archive.
 */
class RestoreWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    companion object {
        const val WORK_NAME = "restore_user_data"
        const val SOURCE_URI_KEY = "restore_source_uri"
        const val INCLUDE_DRAFTS_KEY = "restore_include_drafts"
        const val INCLUDE_MEDIA_KEY = "restore_include_media"
        const val SUMMARY_JSON_KEY = "restore_summary_json"
        const val ERROR_KEY = "restore_error"
    }

    private val restoreUserDataUseCase: RestoreUserDataUseCase by inject()
    private val restoreFromEncryptedBackupUseCase: RestoreFromEncryptedBackupUseCase by inject()
    private val identityKeyManager: IdentityKeyManager by inject()
    private val mediaManager: MediaManager by inject()
    private val restoreLauncher: RestoreLauncher by inject()
    private val json = Json { ignoreUnknownKeys = true }
    private val notificationHelper = RestoreNotificationHelper(context, Uuid.parse(id.toString()))

    private val sourceUri: Uri? = inputData.getString(SOURCE_URI_KEY)?.toUri()
    private val includeDrafts: Boolean = inputData.getBoolean(INCLUDE_DRAFTS_KEY, true)
    private val includeMedia: Boolean = inputData.getBoolean(INCLUDE_MEDIA_KEY, true)

    override suspend fun getForegroundInfo(): ForegroundInfo = notificationHelper.createForegroundInfo(RestoreStage.PREPARING)

    override suspend fun doWork(): Result {
        trySetForeground(getForegroundInfo())
        emitProgress(RestoreStage.PREPARING, 0)

        val restoreUri =
            sourceUri
                ?: return failure("Missing restore source")

        val sourceLabel = context.contentResolver.resolveDisplayName(restoreUri) ?: restoreUri.toString()

        emitProgress(RestoreStage.COPYING_ARCHIVE, 5)
        val tempFile =
            copyToCache(restoreUri)
                ?: return failure("Unable to read restore archive")

        emitProgress(RestoreStage.OPENING_ARCHIVE, 10)
        if (tempFile.isEncryptedBackup()) {
            return restoreEncryptedBackup(tempFile, sourceLabel)
        }

        val zipFile =
            runCatching { ZipFile(tempFile) }
                .getOrElse { error ->
                    tempFile.delete()
                    restoreLauncher.completeRestore(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))
                    return failure("Unable to open restore archive: ${error.message}")
                }

        return try {
            emitProgress(RestoreStage.READING_CONTENTS, 20)

            val bundle =
                RestoreBundle(
                    metadataJson = readRequiredEntry(zipFile, ExportFileStructure.METADATA_FILE),
                    journalsJson = readRequiredEntry(zipFile, ExportFileStructure.JOURNALS_FILE),
                    notesJson = readRequiredEntry(zipFile, ExportFileStructure.NOTES_FILE),
                    journalNotesJson = readRequiredEntry(zipFile, ExportFileStructure.JOURNAL_NOTES_FILE),
                    draftsJson = readRequiredEntry(zipFile, ExportFileStructure.DRAFTS_FILE),
                    profileJson = readOptionalEntry(zipFile, ExportFileStructure.PROFILE_FILE),
                    placesJson = readOptionalEntry(zipFile, ExportFileStructure.PLACES_FILE),
                    locationHistoryJson = readOptionalEntry(zipFile, ExportFileStructure.LOCATION_HISTORY_FILE),
                    mediaManifestJson = readOptionalEntry(zipFile, ExportFileStructure.MEDIA_MANIFEST_FILE),
                )

            emitProgress(RestoreStage.RESTORING_JOURNALS, 40)

            val mediaImporter =
                if (includeMedia) {
                    object : MediaImporter {
                        override suspend fun importMedia(exportPath: String): String? = this@RestoreWorker.importMedia(zipFile, exportPath)
                    }
                } else {
                    null
                }

            val options =
                RestoreOptions(
                    includeDrafts = includeDrafts,
                    includeMedia = includeMedia,
                )

            val result =
                restoreUserDataUseCase.restore(
                    bundle = bundle,
                    options = options,
                    mediaImporter = mediaImporter,
                    onProgress = { phase ->
                        val info = phase.toProgressInfo()
                        restoreLauncher.updateProgress(info)
                        if (info is RestoreProgressInfo.Active) {
                            trySetForeground(notificationHelper.createForegroundInfo(info.stage))
                        }
                    },
                )

            val summary = result.toSummary(source = sourceLabel)

            trySetForeground(notificationHelper.createCompletionInfo())
            restoreLauncher.completeRestore(RestoreOutcome.Success(summary))

            Result.success(
                workDataOf(
                    SUMMARY_JSON_KEY to json.encodeToString(summary),
                ),
            )
        } catch (e: Exception) {
            Napier.e("Restore failed", e)
            trySetForeground(notificationHelper.createErrorInfo(e.message ?: "Restore failed"))
            restoreLauncher.completeRestore(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))
            failure(e.message ?: "Restore failed")
        } finally {
            restoreLauncher.updateProgress(RestoreProgressInfo.Idle)
            zipFile.close()
            tempFile.delete()
        }
    }

    private suspend fun restoreEncryptedBackup(
        file: File,
        sourceLabel: String,
    ): Result {
        val recoveryPhrase =
            identityKeyManager.getStoredRecoveryPhrase()?.words
                ?: run {
                    file.delete()
                    restoreLauncher.completeRestore(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))
                    return failure("Recovery phrase is not available")
                }

        return try {
            val result =
                restoreFromEncryptedBackupUseCase.restore(
                    backupFile = file.absolutePath.toPath(),
                    recoveryPhrase = recoveryPhrase,
                    onProgress = { progress ->
                        when (progress) {
                            RestoreProgress.Starting -> emitProgress(RestoreStage.PREPARING, 0)
                            RestoreProgress.DerivingKeys -> emitProgress(RestoreStage.OPENING_ARCHIVE, 10)
                            RestoreProgress.Decrypting -> emitProgress(RestoreStage.READING_CONTENTS, 20)
                            RestoreProgress.RestoringData -> emitProgress(RestoreStage.RESTORING_JOURNALS, 40)
                            RestoreProgress.Completed -> emitProgress(RestoreStage.IMPORTING_MEDIA, 98)
                            is RestoreProgress.Failed -> Unit
                        }
                    },
                ) ?: throw IllegalStateException("Encrypted restore did not return a result")

            val summary = result.toSummary(source = sourceLabel)
            trySetForeground(notificationHelper.createCompletionInfo())
            restoreLauncher.completeRestore(RestoreOutcome.Success(summary))
            Result.success(
                workDataOf(
                    SUMMARY_JSON_KEY to json.encodeToString(summary),
                ),
            )
        } catch (e: Exception) {
            Napier.e("Encrypted backup restore failed", e)
            trySetForeground(notificationHelper.createErrorInfo(e.message ?: "Restore failed"))
            restoreLauncher.completeRestore(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))
            failure(e.message ?: "Restore failed")
        } finally {
            restoreLauncher.updateProgress(RestoreProgressInfo.Idle)
            file.delete()
        }
    }

    private fun File.isEncryptedBackup(): Boolean =
        runCatching {
            source().buffer().use { source ->
                EncryptedBackupFileFormat.isEncryptedBackup(source)
            }
        }.getOrDefault(false)

    private suspend fun emitProgress(
        stage: RestoreStage,
        percent: Int,
    ) {
        restoreLauncher.updateProgress(stage.toProgressInfo(percent))
        trySetForeground(notificationHelper.createForegroundInfo(stage))
    }

    /**
     * Attempts to set foreground notification. If it fails (e.g. missing
     * POST_NOTIFICATIONS permission), the restore continues without notification.
     */
    private suspend fun trySetForeground(foregroundInfo: ForegroundInfo) {
        try {
            setForeground(foregroundInfo)
        } catch (e: Exception) {
            Napier.w("Could not show foreground notification, restore continues without it", e)
        }
    }

    private fun failure(message: String): Result = Result.failure(workDataOf(ERROR_KEY to message))

    private fun copyToCache(uri: Uri): File? {
        val tempFile = File.createTempFile("logdate_restore", ".zip", context.cacheDir)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            tempFile
        } catch (e: Exception) {
            Napier.e("Failed to copy restore archive to cache", e)
            tempFile.delete()
            null
        }
    }

    private fun readRequiredEntry(
        zipFile: ZipFile,
        entryName: String,
    ): String {
        val entry =
            zipFile.getEntry(entryName)
                ?: throw IllegalStateException("Missing required file: $entryName")
        return zipFile.getInputStream(entry).use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    private fun readOptionalEntry(
        zipFile: ZipFile,
        entryName: String,
    ): String? {
        val entry = zipFile.getEntry(entryName) ?: return null
        return zipFile.getInputStream(entry).use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    private suspend fun importMedia(
        zipFile: ZipFile,
        exportPath: String,
    ): String? {
        val normalizedPath = exportPath.trimStart('/')
        val entry = zipFile.getEntry(normalizedPath)
        if (entry == null) {
            Napier.w("Media file not found in archive at path: $exportPath")
            return null
        }
        if (entry.isDirectory) {
            Napier.w("Expected file but found directory in archive at path: $exportPath")
            return null
        }
        val fileName = normalizedPath.substringAfterLast('/')
        val tempFile = File.createTempFile("logdate_media_", "_$fileName", context.cacheDir)
        try {
            zipFile.getInputStream(entry).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            val fromExtension = resolveMimeType(fileName)
            // Always read magic bytes once and reuse — avoids double file I/O when both
            // the extension path and the verification path would otherwise call detectMimeTypeFromBytes.
            val fromBytes = detectMimeTypeFromBytes(tempFile)
            val mimeType =
                when {
                    fromExtension == "application/octet-stream" -> {
                        if (fromBytes != "application/octet-stream") {
                            Napier.d("Detected MIME type from magic bytes for $fileName: $fromBytes")
                        }
                        fromBytes
                    }
                    fromBytes != "application/octet-stream" && fromBytes != fromExtension -> {
                        // Extension may be inferred (e.g. .jpg for a HEIC file from a bare
                        // MediaStore content URI). Magic bytes win when they disagree.
                        Napier.w(
                            "MIME mismatch for $fileName: extension says $fromExtension " +
                                "but magic bytes say $fromBytes — using $fromBytes",
                        )
                        fromBytes
                    }
                    else -> fromExtension
                }
            val savedPath =
                if (mimeType.startsWith("audio/")) {
                    saveAudioToInternalStorage(tempFile, fileName)
                } else {
                    mediaManager.saveMediaFromFile(
                        sourceFilePath = tempFile.absolutePath,
                        fileName = fileName,
                        mimeType = mimeType,
                    )
                }
            Napier.d("Successfully imported media from archive: $exportPath")
            return savedPath
        } catch (e: Exception) {
            Napier.e("Exception importing media from archive at path: $exportPath - file: $fileName - error: ${e.message}", e)
            return null
        } finally {
            tempFile.delete()
        }
    }

    private fun resolveMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mimeType =
            if (extension.isNotBlank()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            } else {
                null
            }
        return mimeType ?: "application/octet-stream"
    }

    private fun saveAudioToInternalStorage(
        sourceFile: File,
        fileName: String,
    ): String {
        val audioDir = File(context.filesDir, "audio_notes").apply { mkdirs() }
        val destFile = File(audioDir, fileName)
        sourceFile.copyTo(destFile, overwrite = true)
        return Uri.fromFile(destFile).toString()
    }

    private fun detectMimeTypeFromBytes(file: File): String =
        try {
            val header = ByteArray(16)
            file.inputStream().use { it.read(header) }
            when {
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() ->
                    "image/jpeg"
                header[0] == 0x89.toByte() &&
                    header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() &&
                    header[3] == 0x47.toByte() ->
                    "image/png"
                header[0] == 0x52.toByte() &&
                    header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte() &&
                    header[3] == 0x46.toByte() &&
                    header[8] == 0x57.toByte() &&
                    header[9] == 0x45.toByte() &&
                    header[10] == 0x42.toByte() &&
                    header[11] == 0x50.toByte() ->
                    "image/webp"
                header[0] == 0x47.toByte() &&
                    header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte() &&
                    header[3] == 0x38.toByte() ->
                    "image/gif"
                header[4] == 0x66.toByte() &&
                    header[5] == 0x74.toByte() &&
                    header[6] == 0x79.toByte() &&
                    header[7] == 0x70.toByte() -> {
                    val brand = String(header, 8, 4, Charsets.US_ASCII)
                    when {
                        brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("avi") -> "image/heic"
                        brand.startsWith("M4A") || brand.startsWith("m4a") -> "audio/mp4"
                        else -> "video/mp4"
                    }
                }
                else -> "application/octet-stream"
            }
        } catch (e: Exception) {
            Napier.w("Failed to detect MIME type from magic bytes: ${file.name}", e)
            "application/octet-stream"
        }
}
