package app.logdate.feature.core.export

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportIssue
import app.logdate.client.domain.export.ExportIssueCode
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportStage
import app.logdate.client.domain.export.ExportUserDataUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.catch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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

    private data class SavedExport(
        val path: String,
    )

    private data class ResolvedMediaInput(
        val openStream: () -> InputStream,
        val issueCode: ExportIssueCode? = null,
    )

    private data class MediaWriteResult(
        val written: Boolean,
        val issue: ExportIssue? = null,
    )

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
                            } catch (e: Exception) {
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
        } catch (exception: Exception) {
            Napier.e("Worker execution failed", exception)
            trySetForeground(notificationHelper.createErrorInfo(WORKER_FAILED_MESSAGE))
            failureResult(WORKER_FAILED_MESSAGE)
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
            ZipOutputStream(outputStream).use { zipOut ->
                writeExportToZip(zipOut, exportData)
            }
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")

        return SavedExport(
            path = uri.toString(),
        )
    }

    private fun saveToDownloads(exportData: ExportResult): SavedExport {
        val fileName = generateExportFileName()

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        FileOutputStream(file).use { fileOut ->
            ZipOutputStream(fileOut).use { zipOut ->
                writeExportToZip(zipOut, exportData)
            }
        }

        return SavedExport(
            path = file.absolutePath,
        )
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

        val exportedMediaFiles = mutableListOf<ExportMediaFile>()
        val archiveIssues = mutableListOf<ExportIssue>()
        exportData.mediaFiles.forEach { mediaFile ->
            val outcome = writeMediaEntry(zipOut, mediaFile)
            if (outcome.written) {
                exportedMediaFiles += mediaFile
            }
            outcome.issue?.let(archiveIssues::add)
        }

        exportData.serializeMediaManifest(exportedMediaFiles)?.let { manifest ->
            writeJsonEntry(zipOut, ExportFileStructure.MEDIA_MANIFEST_FILE, manifest)
        }
        exportData.renderIssuesText(archiveIssues)?.let { issuesText ->
            writeJsonEntry(zipOut, ExportFileStructure.EXPORT_ISSUES_FILE, issuesText)
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
        mediaFile: ExportMediaFile,
    ): MediaWriteResult {
        val sourceUri = mediaFile.sourceUri
        var entryOpened = false
        return try {
            val resolvedInput =
                resolveMediaInput(mediaFile)
                    ?: return MediaWriteResult(
                        written = false,
                        issue =
                            ExportIssue(
                                code = ExportIssueCode.MEDIA_BYTES_MISSING,
                                source = sourceUri,
                            ),
                    )
            zipOut.putNextEntry(ZipEntry(mediaFile.exportPath))
            entryOpened = true
            resolvedInput.openStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
            entryOpened = false
            Napier.d("Added media file to archive: ${mediaFile.exportPath}")
            MediaWriteResult(
                written = true,
                issue =
                    resolvedInput.issueCode?.let { code ->
                        ExportIssue(
                            code = code,
                            source = sourceUri,
                        )
                    },
            )
        } catch (e: Exception) {
            if (entryOpened) {
                runCatching { zipOut.closeEntry() }
            }
            Napier.w("Failed to add media file to ZIP, skipping: $sourceUri", e)
            MediaWriteResult(
                written = false,
                issue =
                    ExportIssue(
                        code = ExportIssueCode.MEDIA_BYTES_MISSING,
                        source = sourceUri,
                        detail = e.message,
                    ),
            )
        }
    }

    private fun resolveMediaInput(mediaFile: ExportMediaFile): ResolvedMediaInput? {
        val sourceUri = mediaFile.sourceUri
        if (!sourceUri.startsWith("/") && !sourceUri.startsWith("file://")) {
            val parsed = sourceUri.toUri()
            return ResolvedMediaInput(
                openStream = {
                    context.contentResolver.openInputStream(parsed)
                        ?: throw IllegalStateException("Media file cannot be opened: $sourceUri")
                },
            )
        }

        val originalFile = File(sourceUri.removePrefix("file://"))
        if (originalFile.exists()) {
            return ResolvedMediaInput(openStream = { originalFile.inputStream() })
        }

        val recovered = recoverMediaInput(originalFile, mediaFile)
        if (recovered != null) {
            Napier.w("Recovered stale export media reference: $sourceUri")
            return recovered
        }

        Napier.w("Media file not found, skipping: ${originalFile.absolutePath}")
        return null
    }

    private fun recoverMediaInput(
        originalFile: File,
        mediaFile: ExportMediaFile,
    ): ResolvedMediaInput? {
        val normalizedFile = normalizeDuplicateExtension(originalFile)
        if (normalizedFile != originalFile && normalizedFile.exists()) {
            return ResolvedMediaInput(
                openStream = { normalizedFile.inputStream() },
                issueCode = ExportIssueCode.MEDIA_RECOVERED_NORMALIZED_PATH,
            )
        }

        val recordingFileName = extractRecordingFileName(originalFile.name)
        if (recordingFileName != null) {
            val audioNotesFile = File(context.filesDir, "audio_notes/$recordingFileName")
            if (audioNotesFile.exists()) {
                return ResolvedMediaInput(
                    openStream = { audioNotesFile.inputStream() },
                    issueCode = ExportIssueCode.MEDIA_RECOVERED_APP_PRIVATE_AUDIO,
                )
            }
        }

        recoverAppPrivateMedia(originalFile)?.let { recoveredFile ->
            return ResolvedMediaInput(
                openStream = { recoveredFile.inputStream() },
                issueCode = ExportIssueCode.MEDIA_RECOVERED_APP_PRIVATE_MEDIA,
            )
        }

        val mediaStoreUri = recoverMediaStoreUri(originalFile.name, mediaFile.exportPath)
        if (mediaStoreUri != null) {
            return ResolvedMediaInput(
                openStream = {
                    context.contentResolver.openInputStream(mediaStoreUri)
                        ?: throw IllegalStateException("Recovered media URI cannot be opened: $mediaStoreUri")
                },
                issueCode = ExportIssueCode.MEDIA_RECOVERED_MEDIA_STORE,
            )
        }

        return null
    }

    private fun recoverAppPrivateMedia(originalFile: File): File? {
        val userMediaDir = File(context.filesDir, "user_media")
        val candidates = userMediaDir.listFiles().orEmpty()
        if (candidates.isEmpty()) return null

        val fileName = originalFile.name
        val baseName = fileName.substringBeforeLast('.', fileName)
        val trailingToken = fileName.substringAfterLast('_', "")
        val trailingStem = trailingToken.substringBeforeLast('.', trailingToken)

        val matchKeys =
            listOf(
                fileName,
                baseName,
                trailingToken,
                trailingStem,
            ).filter { it.isNotBlank() }
                .distinct()

        return candidates.firstOrNull { candidate ->
            val candidateName = candidate.name
            matchKeys.any { key ->
                candidateName == key ||
                    candidateName.startsWith("$key.") ||
                    candidateName.contains("_$key")
            }
        }
    }

    private fun normalizeDuplicateExtension(file: File): File {
        val normalizedName =
            file.name.replace(Regex("(\\.[A-Za-z0-9]+)\\1$")) { match ->
                match.groupValues[1]
            }
        return if (normalizedName == file.name) file else File(file.parentFile, normalizedName)
    }

    private fun extractRecordingFileName(fileName: String): String? {
        val match =
            Regex("(recording_[A-Za-z0-9-]+(?:\\.[A-Za-z0-9]+)?)")
                .find(fileName)
                ?: return null
        val normalized =
            match.value.replace(Regex("(\\.[A-Za-z0-9]+)\\1$")) { group ->
                group.groupValues[1]
            }
        return if ('.' in normalized) normalized else "$normalized.m4a"
    }

    private fun recoverMediaStoreUri(
        fileName: String,
        exportPath: String,
    ): Uri? {
        val legacyId = Regex("(\\d{6,})$").find(fileName)?.groupValues?.get(1) ?: return null
        val targetCollection =
            when {
                exportPath.endsWith(".jpg") || exportPath.endsWith(".jpeg") || exportPath.endsWith(".png") ->
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                exportPath.endsWith(".mp4") || exportPath.endsWith(".mov") ->
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                exportPath.endsWith(".m4a") || exportPath.endsWith(".aac") || exportPath.endsWith(".wav") ->
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> return null
            }
        return Uri.withAppendedPath(targetCollection, legacyId)
    }
}
