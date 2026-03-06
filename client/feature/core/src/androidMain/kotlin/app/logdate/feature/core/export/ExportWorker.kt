package app.logdate.feature.core.export

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportUserDataUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Clock

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
        const val DATE_RANGE_KEY = "date_range"
    }

    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val exportLauncher: ExportLauncher by inject()
    private val notificationHelper = ExportNotificationHelper(context, id)

    // Get destination URI from input data if available
    private val destinationUri: Uri? = inputData.getString(DESTINATION_URI_KEY)?.toUri()

    // Read export options from input data
    private val includeJournals: Boolean = inputData.getBoolean(INCLUDE_JOURNALS_KEY, true)
    private val includeNotes: Boolean = inputData.getBoolean(INCLUDE_NOTES_KEY, true)
    private val includeDrafts: Boolean = inputData.getBoolean(INCLUDE_DRAFTS_KEY, true)
    private val includeMedia: Boolean = inputData.getBoolean(INCLUDE_MEDIA_KEY, true)
    private val dateRangeCutoff =
        ExportUserDataUseCase.resolveDateRangeCutoff(
            inputData.getString(DATE_RANGE_KEY) ?: "all_time",
        )

    override suspend fun doWork(): Result {
        // Try to promote to foreground service — if this fails (e.g. missing
        // POST_NOTIFICATIONS permission on Android 13+), the export still runs.
        trySetForeground(getForegroundInfo())

        return try {
            var finalResult = Result.success()

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
                            Napier.i("ExportWorker: Progress $progressInt% - ${progress.message}")
                            trySetForeground(notificationHelper.createForegroundInfo(progressInt, progress.message))
                            emitProgress(progressInt, progress.message)
                        }

                        is ExportProgress.Completed -> {
                            try {
                                Napier.i("ExportWorker: Completed, saving file...")
                                trySetForeground(notificationHelper.createForegroundInfo(90, "Creating ZIP archive..."))
                                emitProgress(90, "Creating ZIP archive...")

                                val filePath =
                                    if (destinationUri != null) {
                                        saveToUri(progress.result, destinationUri)
                                    } else {
                                        saveToDownloads(progress.result)
                                    }

                                trySetForeground(notificationHelper.createCompletionInfo(filePath))
                                emitProgress(100, "Export completed")

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
                            Napier.e("ExportWorker: Failed - ${progress.reason}")
                            trySetForeground(notificationHelper.createErrorInfo(progress.reason))
                            finalResult =
                                Result.failure(
                                    workDataOf(
                                        ERROR_KEY to progress.reason,
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

    /**
     * Save export data to a ZIP file at the content URI that the user selected
     */
    private fun saveToUri(
        exportData: ExportResult,
        uri: Uri,
    ): String {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                zipOut.putNextEntry(ZipEntry("metadata.json"))
                zipOut.write(exportData.metadata.toByteArray())
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("journals.json"))
                zipOut.write(exportData.journals.toByteArray())
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("notes.json"))
                zipOut.write(exportData.notes.toByteArray())
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("journal_notes.json"))
                zipOut.write(exportData.journalNotes.toByteArray())
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("drafts.json"))
                zipOut.write(exportData.drafts.toByteArray())
                zipOut.closeEntry()

                exportData.mediaManifest?.let { manifest ->
                    zipOut.putNextEntry(ZipEntry("media_manifest.json"))
                    zipOut.write(manifest.toByteArray())
                    zipOut.closeEntry()
                }

                exportData.mediaFiles.forEach { mediaFile ->
                    try {
                        zipOut.putNextEntry(ZipEntry(mediaFile.exportPath))
                        val sourceUri = mediaFile.sourceUri
                        if (sourceUri.startsWith("/") || sourceUri.startsWith("file://")) {
                            val file = java.io.File(sourceUri.removePrefix("file://"))
                            file.inputStream().use { it.copyTo(zipOut) }
                        } else {
                            context.contentResolver.openInputStream(sourceUri.toUri())?.use { inputStream ->
                                inputStream.copyTo(zipOut)
                            }
                        }
                        zipOut.closeEntry()
                    } catch (e: Exception) {
                        Napier.e("Failed to add media file to ZIP: ${mediaFile.sourceUri}", e)
                    }
                }
            }
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")

        return uri.toString()
    }

    /**
     * Fall back method to save to the Downloads directory if no URI was selected
     */
    private fun saveToDownloads(exportData: ExportResult): String {
        val timestamp =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .let { "${it.year}-${it.month.number.toString().padStart(2, '0')}-${it.day.toString().padStart(2, '0')}" }

        val fileName = "logdate-export-$timestamp.zip"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        FileOutputStream(file).use { fileOut ->
            ZipOutputStream(fileOut).use { zipOut ->
                zipOut.putNextEntry(ZipEntry("metadata.json"))
                zipOut.write(exportData.metadata.toByteArray())
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("journals.json"))
                zipOut.write(exportData.journals.toByteArray())
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("notes.json"))
                zipOut.write(exportData.notes.toByteArray())
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("journal_notes.json"))
                zipOut.write(exportData.journalNotes.toByteArray())
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("drafts.json"))
                zipOut.write(exportData.drafts.toByteArray())
                zipOut.closeEntry()

                exportData.mediaManifest?.let { manifest ->
                    zipOut.putNextEntry(ZipEntry("media_manifest.json"))
                    zipOut.write(manifest.toByteArray())
                    zipOut.closeEntry()
                }

                exportData.mediaFiles.forEach { mediaFile ->
                    try {
                        zipOut.putNextEntry(ZipEntry(mediaFile.exportPath))
                        val sourceUri = mediaFile.sourceUri
                        if (sourceUri.startsWith("/") || sourceUri.startsWith("file://")) {
                            val file = java.io.File(sourceUri.removePrefix("file://"))
                            file.inputStream().use { it.copyTo(zipOut) }
                        } else {
                            context.contentResolver.openInputStream(sourceUri.toUri())?.use { inputStream ->
                                inputStream.copyTo(zipOut)
                            }
                        }
                        zipOut.closeEntry()
                    } catch (e: Exception) {
                        Napier.e("Failed to add media file to ZIP: ${mediaFile.sourceUri}", e)
                    }
                }
            }
        }

        return file.absolutePath
    }
}
