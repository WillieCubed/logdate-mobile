package app.logdate.feature.core.restore

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
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

    override suspend fun doWork(): Result {
        emitProgress(RestoreStage.PREPARING, 0)

        val restoreUri =
            sourceUri
                ?: return failure("Missing restore source")

        val sourceLabel = resolveDisplayName(restoreUri) ?: restoreUri.toString()

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

            val structure = ExportFileStructure()
            val bundle =
                RestoreBundle(
                    metadataJson = readRequiredEntry(zipFile, structure.metadataFile),
                    journalsJson = readRequiredEntry(zipFile, structure.journalsFile),
                    notesJson = readRequiredEntry(zipFile, structure.notesFile),
                    journalNotesJson = readRequiredEntry(zipFile, structure.journalNotesFile),
                    draftsJson = readRequiredEntry(zipFile, structure.draftsFile),
                    profileJson = readOptionalEntry(zipFile, structure.profileFile),
                    placesJson = readOptionalEntry(zipFile, structure.placesFile),
                    locationHistoryJson = readOptionalEntry(zipFile, structure.locationHistoryFile),
                    mediaManifestJson = readOptionalEntry(zipFile, structure.mediaManifestFile),
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
                            setForeground(notificationHelper.createForegroundInfo(info.stage))
                        }
                    },
                )

            val summary = result.toSummary(source = sourceLabel)

            setForeground(notificationHelper.createCompletionInfo())

            Result.success(
                workDataOf(
                    SUMMARY_JSON_KEY to json.encodeToString(summary),
                ),
            )
        } catch (e: Exception) {
            Napier.e("Restore failed", e)
            setForeground(notificationHelper.createErrorInfo(e.message ?: "Restore failed"))
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
        setForeground(notificationHelper.createForegroundInfo(stage))
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
        val data = zipFile.getInputStream(entry).use { input -> input.readBytes() }
        val fileName = normalizedPath.substringAfterLast('/')
        val payload =
            MediaPayload(
                fileName = fileName,
                mimeType = resolveMimeType(fileName),
                sizeBytes = data.size.toLong(),
                data = data,
            )
        return runCatching { mediaManager.saveMedia(payload) }
            .onFailure { Napier.e("Failed to import media during restore", it) }
            .getOrNull()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val resolver = context.contentResolver
        val cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return it.getString(index)
                }
            }
        }
        return null
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
