package app.logdate.feature.core.restore

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

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
    private val mediaManager: MediaManager by inject()
    private val restoreLauncher: RestoreLauncher by inject()
    private val json = Json { ignoreUnknownKeys = true }
    private val notificationHelper = RestoreNotificationHelper(context, id)

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
        val zipFile =
            runCatching { ZipFile(tempFile) }
                .getOrElse { error ->
                    tempFile.delete()
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

            Result.success(
                workDataOf(
                    SUMMARY_JSON_KEY to json.encodeToString(summary),
                ),
            )
        } catch (e: Exception) {
            Napier.e("Restore failed", e)
            trySetForeground(notificationHelper.createErrorInfo(e.message ?: "Restore failed"))
            failure(e.message ?: "Restore failed")
        } finally {
            restoreLauncher.updateProgress(RestoreProgressInfo.Idle)
            zipFile.close()
            tempFile.delete()
        }
    }

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
        val entry = zipFile.getEntry(normalizedPath) ?: return null
        if (entry.isDirectory) {
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
            return mediaManager.saveMediaFromFile(
                sourceFilePath = tempFile.absolutePath,
                fileName = fileName,
                mimeType = resolveMimeType(fileName),
            )
        } catch (e: Exception) {
            Napier.e("Failed to import media during restore", e)
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
}
