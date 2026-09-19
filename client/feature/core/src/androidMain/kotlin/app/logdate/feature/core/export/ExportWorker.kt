package app.logdate.feature.core.export

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportStage
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.client.domain.export.archive.ArchiveExportOptions
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ArchiveExportSummary
import app.logdate.client.domain.export.archive.ExportArchiveUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.catch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlin.uuid.Uuid

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
        private const val EXPORT_FAILED_MESSAGE = "Export could not be completed."
        private const val ARCHIVE_WRITE_FAILED_MESSAGE = "Could not write the export archive."
        private const val WORKER_FAILED_MESSAGE = "Export worker failed."
        const val DESTINATION_URI_KEY = "destination_uri"
        const val INCLUDE_JOURNALS_KEY = "include_journals"
        const val INCLUDE_NOTES_KEY = "include_notes"
        const val INCLUDE_DRAFTS_KEY = "include_drafts"
        const val INCLUDE_MEDIA_KEY = "include_media"
        const val DATE_CUTOFF_MILLIS_KEY = "date_cutoff_millis"
    }

    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val exportArchiveUseCase: ExportArchiveUseCase by inject()
    private val featureFlags: FeatureFlagStore by inject()
    private val exportLauncher: ExportLauncher by inject()
    private val archiveWriter = AndroidExportArchiveWriter(context)
    private val notificationHelper = ExportNotificationHelper(context, Uuid.parse(id.toString()))

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

    private data class SavedExport(
        val path: String,
    )

    override suspend fun doWork(): Result {
        Napier.i("ExportWorker.doWork() STARTED - export worker is executing")

        // Try to promote to foreground service — if this fails (e.g. missing
        // POST_NOTIFICATIONS permission on Android 13+), the export still runs.
        trySetForeground(getForegroundInfo())
        Napier.i("ExportWorker: Foreground service setup complete")

        if (featureFlags.isEnabled(FeatureFlag.EXPORT_ARCHIVE_V2)) return runArchiveExport()

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
                    finalResult = failureResult(EXPORT_FAILED_MESSAGE)
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
                                val archiveMessage = ExportStage.WRITING_ARCHIVE.defaultMessage
                                trySetForeground(notificationHelper.createForegroundInfo(90, archiveMessage))
                                emitProgress(90, archiveMessage)

                                val savedExport =
                                    if (destinationUri != null) {
                                        saveToUri(progress.result, destinationUri)
                                    } else {
                                        saveToDownloads(progress.result)
                                    }

                                trySetForeground(notificationHelper.createCompletionInfo(savedExport.path))
                                exportLauncher.updateProgress(
                                    ExportProgressInfo(
                                        isActive = false,
                                        progressPercent = 100,
                                        message = "Export completed",
                                        completedFilePath = savedExport.path,
                                        stats = progress.result.stats,
                                    ),
                                )

                                finalResult =
                                    Result.success(
                                        workDataOf(
                                            PROGRESS_KEY to 100,
                                            MESSAGE_KEY to "Export completed",
                                            FILE_PATH_KEY to savedExport.path,
                                        ),
                                    )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                // Throwable (not just Exception) so an OutOfMemoryError while
                                // writing the archive fails the export with a real reason instead
                                // of escaping uncaught as a bare, reasonless failure.
                                Napier.e("Failed to save file", e)
                                trySetForeground(notificationHelper.createErrorInfo(ARCHIVE_WRITE_FAILED_MESSAGE))
                                finalResult = failureResult(ARCHIVE_WRITE_FAILED_MESSAGE)
                            }
                        }

                        is ExportProgress.Failed -> {
                            val errorMessage = progress.error.defaultMessage
                            Napier.e("ExportWorker: Failed - $errorMessage")
                            trySetForeground(notificationHelper.createErrorInfo(errorMessage))
                            finalResult = failureResult(errorMessage)
                        }
                    }
                }

            Napier.i("ExportWorker: doWork() finishing with result: $finalResult")
            finalResult
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            Napier.e("Worker execution failed", exception)
            trySetForeground(notificationHelper.createErrorInfo(WORKER_FAILED_MESSAGE))
            failureResult(WORKER_FAILED_MESSAGE)
        }
    }

    /** Where a 2.0 archive is written, and how to remove it if the export does not finish. */
    private class ArchiveTarget(
        val stream: OutputStream,
        val path: String,
        val discard: () -> Unit,
    )

    private fun openArchiveTarget(): ArchiveTarget? {
        val destination = destinationUri
        if (destination != null) {
            val stream = context.contentResolver.openOutputStream(destination) ?: return null
            return ArchiveTarget(stream, destination.toString()) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            }
        }
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), generateExportFileName())
        return ArchiveTarget(FileOutputStream(file), file.absolutePath) { file.delete() }
    }

    /**
     * Writes a 2.0 archive straight into its destination. If the export fails or is cancelled the
     * partly written file is removed, so a truncated archive never sits where a real one should.
     */
    private suspend fun runArchiveExport(): Result {
        val target =
            openArchiveTarget() ?: run {
                trySetForeground(notificationHelper.createErrorInfo(ARCHIVE_WRITE_FAILED_MESSAGE))
                return failureResult(ARCHIVE_WRITE_FAILED_MESSAGE)
            }
        var finished = false
        try {
            var summary: ArchiveExportSummary? = null
            var failure: String? = null
            val options = ArchiveExportOptions(includeJournals, includeNotes, includeDrafts, includeMedia, from = dateRangeCutoff)
            ZipOutputStream(target.stream.buffered()).use { zip ->
                exportArchiveUseCase.export(options, ZipStreamArchiveContainer(zip)).collect { progress ->
                    when (progress) {
                        ArchiveExportProgress.Starting -> {
                            trySetForeground(notificationHelper.createForegroundInfo(0, "Starting export..."))
                            emitProgress(0, "Starting export...")
                        }
                        is ArchiveExportProgress.InProgress -> {
                            val percent = (progress.fraction * 100).toInt()
                            trySetForeground(notificationHelper.createForegroundInfo(percent, progress.stage.defaultMessage))
                            emitProgress(percent, progress.stage.defaultMessage)
                        }
                        is ArchiveExportProgress.Completed -> summary = progress.summary
                        is ArchiveExportProgress.Failed -> failure = progress.error.defaultMessage
                    }
                }
            }
            val completed = summary
            if (failure != null || completed == null) {
                val message = failure ?: EXPORT_FAILED_MESSAGE
                trySetForeground(notificationHelper.createErrorInfo(message))
                return failureResult(message)
            }

            finished = true
            trySetForeground(notificationHelper.createCompletionInfo(target.path))
            exportLauncher.updateProgress(
                ExportProgressInfo(
                    isActive = false,
                    progressPercent = 100,
                    message = "Export completed",
                    completedFilePath = target.path,
                    stats = completed.counts.toExportStats(),
                ),
            )
            return Result.success(workDataOf(PROGRESS_KEY to 100, MESSAGE_KEY to "Export completed", FILE_PATH_KEY to target.path))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Throwable) {
            Napier.e("Archive export failed", exception)
            trySetForeground(notificationHelper.createErrorInfo(ARCHIVE_WRITE_FAILED_MESSAGE))
            return failureResult(ARCHIVE_WRITE_FAILED_MESSAGE)
        } finally {
            if (!finished) target.discard()
        }
    }

    private fun failureResult(message: String): Result =
        Result.failure(
            workDataOf(
                ERROR_KEY to message,
            ),
        )

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
    ): SavedExport {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            archiveWriter.write(exportData, outputStream)
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")

        return SavedExport(
            path = uri.toString(),
        )
    }

    private fun saveToDownloads(exportData: ExportResult): SavedExport {
        val fileName = generateExportFileName()

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        FileOutputStream(file).use { fileOut -> archiveWriter.write(exportData, fileOut) }

        return SavedExport(
            path = file.absolutePath,
        )
    }
}
