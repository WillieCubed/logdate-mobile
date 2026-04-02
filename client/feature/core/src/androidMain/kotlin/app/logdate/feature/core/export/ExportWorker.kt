package app.logdate.feature.core.export

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportUserDataUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.catch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Instant

/**
 * WorkManager worker for exporting user data according to the LogDate export specification.
 */
class ExportWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    companion object {
        const val WORK_NAME = "export_user_data"
        const val PROGRESS_KEY = "export_progress"
        const val MESSAGE_KEY = "export_message"
        const val FILE_PATH_KEY = "export_file_path"
        const val ERROR_KEY = "export_error"
        const val DESTINATION_URI_KEY = "destination_uri"
        const val INCLUDE_JOURNALS_KEY = "include_journals"
        const val INCLUDE_NOTES_KEY = "include_notes"
        const val INCLUDE_DRAFTS_KEY = "include_drafts"
        const val INCLUDE_MEDIA_KEY = "include_media"
        const val DATE_CUTOFF_MILLIS_KEY = "date_cutoff_millis"
    }

    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val exportLauncher: ExportLauncher by inject()
    private val notificationHelper = ExportNotificationHelper(context, id)

    private val destinationUri: Uri? = inputData.getString(DESTINATION_URI_KEY)?.toUri()
    private val includeJournals: Boolean = inputData.getBoolean(INCLUDE_JOURNALS_KEY, true)
    private val includeNotes: Boolean = inputData.getBoolean(INCLUDE_NOTES_KEY, true)
    private val includeDrafts: Boolean = inputData.getBoolean(INCLUDE_DRAFTS_KEY, true)
    private val includeMedia: Boolean = inputData.getBoolean(INCLUDE_MEDIA_KEY, true)
    private val dateRangeCutoff: Instant? =
        inputData
            .getLong(DATE_CUTOFF_MILLIS_KEY, -1L)
            .takeIf { it >= 0 }
            ?.let { Instant.fromEpochMilliseconds(it) }

    override suspend fun doWork(): Result {
        Napier.i("ExportWorker.doWork() STARTED - export worker is executing")

        // Try to promote to foreground service — if this fails (e.g. missing
        // POST_NOTIFICATIONS permission on Android 13+), the export still runs.
        trySetForeground(getForegroundInfo())
        Napier.i("ExportWorker: Foreground service setup complete")

        return try {
            var finalResult = Result.success()
            Napier.i("ExportWorker: About to call exportUserDataUseCase.exportUserData()")

            exportUserDataUseCase
                .exportUserData(
                    includeJournals = includeJournals,
                    includeNotes = includeNotes,
                    includeDrafts = includeDrafts,
                    includeMedia = includeMedia,
                    dateRangeCutoff = dateRangeCutoff,
                ).catch { exception ->
                    Napier.e("Export failed", exception)
                    emitProgress(0, "Export failed: ${exception.message}")
                    finalResult =
                        Result.failure(
                            workDataOf(
                                ERROR_KEY to (exception.message ?: "Unknown error occurred"),
                            ),
                        )
                }.collect { progress ->
                    when (progress) {
                        is ExportProgress.Starting -> {
                            Napier.i("ExportWorker: Starting")
                            trySetForeground(notificationHelper.createForegroundInfo(0, "Starting export..."))
                            emitProgress(0, "Starting export...")
                        }

                        is ExportProgress.InProgress -> {
                            val progressInt = (progress.percentage * 100).toInt()
                            val stageMessage = progress.stage.defaultMessage
                            Napier.i("ExportWorker: Progress $progressInt% - $stageMessage")
                            trySetForeground(notificationHelper.createForegroundInfo(progressInt, stageMessage))
                            emitProgress(progressInt, stageMessage)
                        }

                        is ExportProgress.Completed -> {
                            try {
                                Napier.i("ExportWorker: Completed, saving file...")
                                val archiveMessage = app.logdate.client.domain.export.ExportStage.WRITING_ARCHIVE.defaultMessage
                                trySetForeground(notificationHelper.createForegroundInfo(90, archiveMessage))
                                emitProgress(90, archiveMessage)

                                val filePath =
                                    if (destinationUri != null) {
                                        saveToUri(progress.result, destinationUri)
                                    } else {
                                        saveToDownloads(progress.result)
                                    }

                                trySetForeground(notificationHelper.createCompletionInfo(filePath))
                                exportLauncher.updateProgress(
                                    ExportProgressInfo(
                                        isActive = false,
                                        progressPercent = 100,
                                        message = "Export completed",
                                        completedFilePath = filePath,
                                        stats = progress.result.stats,
                                    ),
                                )

                                finalResult =
                                    Result.success(
                                        workDataOf(
                                            PROGRESS_KEY to 100,
                                            MESSAGE_KEY to "Export completed",
                                            FILE_PATH_KEY to filePath,
                                        ),
                                    )
                            } catch (e: Exception) {
                                Napier.e("Failed to save file", e)
                                trySetForeground(notificationHelper.createErrorInfo("Failed to save file: ${e.message}"))
                                finalResult =
                                    Result.failure(
                                        workDataOf(
                                            ERROR_KEY to "Failed to save file: ${e.message}",
                                        ),
                                    )
                            }
                        }

                        is ExportProgress.Failed -> {
                            val errorMessage = progress.error.defaultMessage
                            Napier.e("ExportWorker: Failed - $errorMessage")
                            trySetForeground(notificationHelper.createErrorInfo(errorMessage))
                            finalResult =
                                Result.failure(
                                    workDataOf(
                                        ERROR_KEY to errorMessage,
                                    ),
                                )
                        }
                    }
                }

            Napier.i("ExportWorker: doWork() finishing with result: $finalResult")
            finalResult
        } catch (exception: Exception) {
            Napier.e("Worker execution failed", exception)
            trySetForeground(notificationHelper.createErrorInfo(exception.message ?: "Worker execution failed"))
            Result.failure(
                workDataOf(
                    ERROR_KEY to (exception.message ?: "Worker execution failed"),
                ),
            )
        }
    }

    /**
     * Updates in-app progress via the injected [ExportLauncher], which
     * the ViewModel observes through its [ExportLauncher.exportProgress] flow.
     */
    private fun emitProgress(
        percent: Int,
        message: String,
    ) {
        exportLauncher.updateProgress(
            ExportProgressInfo(
                isActive = true,
                progressPercent = percent,
                message = message,
            ),
        )
    }

    /**
     * Attempts to set foreground notification. If it fails (e.g. missing
     * POST_NOTIFICATIONS permission), the export continues without notification.
     */
    private suspend fun trySetForeground(foregroundInfo: ForegroundInfo) {
        try {
            setForeground(foregroundInfo)
        } catch (e: Exception) {
            Napier.w("Could not show foreground notification, export continues without it", e)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notificationHelper.createForegroundInfo(
            progress = 0,
            message = "Starting export...",
        )

    private fun saveToUri(
        exportData: ExportResult,
        uri: Uri,
    ): String {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                writeExportToZip(zipOut, exportData)
            }
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")

        return uri.toString()
    }

    private fun saveToDownloads(exportData: ExportResult): String {
        val fileName = generateExportFileName()

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        FileOutputStream(file).use { fileOut ->
            ZipOutputStream(fileOut).use { zipOut ->
                writeExportToZip(zipOut, exportData)
            }
        }

        return file.absolutePath
    }

    private fun writeExportToZip(
        zipOut: ZipOutputStream,
        exportData: ExportResult,
    ) {
        writeJsonEntry(zipOut, ExportFileStructure.METADATA_FILE, exportData.serializeMetadata())
        writeJsonEntry(zipOut, ExportFileStructure.JOURNALS_FILE, exportData.serializeJournals())
        writeJsonEntry(zipOut, ExportFileStructure.NOTES_FILE, exportData.serializeNotes())
        writeJsonEntry(zipOut, ExportFileStructure.JOURNAL_NOTES_FILE, exportData.serializeJournalNotes())
        writeJsonEntry(zipOut, ExportFileStructure.DRAFTS_FILE, exportData.serializeDrafts())
        exportData.serializeProfile()?.let { writeJsonEntry(zipOut, ExportFileStructure.PROFILE_FILE, it) }
        exportData.serializePlaces()?.let { writeJsonEntry(zipOut, ExportFileStructure.PLACES_FILE, it) }
        exportData.serializeLocationHistory()?.let { writeJsonEntry(zipOut, ExportFileStructure.LOCATION_HISTORY_FILE, it) }

        exportData.serializeMediaManifest()?.let { manifest ->
            writeJsonEntry(zipOut, ExportFileStructure.MEDIA_MANIFEST_FILE, manifest)
        }

        exportData.mediaFiles.forEach { mediaFile ->
            writeMediaEntry(zipOut, mediaFile)
        }
    }

    private fun writeJsonEntry(
        zipOut: ZipOutputStream,
        entryName: String,
        content: String,
    ) {
        zipOut.putNextEntry(ZipEntry(entryName))
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
    }

    private fun writeMediaEntry(
        zipOut: ZipOutputStream,
        mediaFile: app.logdate.client.domain.export.ExportMediaFile,
    ) {
        val sourceUri = mediaFile.sourceUri
        try {
            if (sourceUri.startsWith("/") || sourceUri.startsWith("file://")) {
                val file = java.io.File(sourceUri.removePrefix("file://"))
                if (!file.exists()) {
                    Napier.w("Media file not found, skipping: ${file.absolutePath}")
                    return
                }
                zipOut.putNextEntry(ZipEntry(mediaFile.exportPath))
                file.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
                Napier.d("Added media file to archive: ${mediaFile.exportPath}")
            } else {
                val inputStream = context.contentResolver.openInputStream(sourceUri.toUri())
                if (inputStream == null) {
                    Napier.w("Media file cannot be opened, skipping: $sourceUri")
                    return
                }
                zipOut.putNextEntry(ZipEntry(mediaFile.exportPath))
                inputStream.use { it.copyTo(zipOut) }
                zipOut.closeEntry()
                Napier.d("Added media file to archive: ${mediaFile.exportPath}")
            }
        } catch (e: Exception) {
            Napier.w("Failed to add media file to ZIP, skipping: $sourceUri", e)
        }
    }
}
