@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package app.logdate.feature.core.export

import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportUserDataUseCase
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import kotlin.time.Clock

/**
 * iOS-specific implementation for launching data export.
 *
 * This implementation creates a temporary directory and presents the iOS share sheet
 * to let the user choose where to save it.
 */
@OptIn(ExperimentalForeignApi::class)
class IosExportLauncher(
    private val rootViewController: () -> UIViewController,
) : ExportLauncher,
    KoinComponent {
    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentExportJob: Job? = null
    private var completionCallback: ((String?) -> Unit)? = null

    private val _exportProgress = MutableStateFlow(ExportProgressInfo())
    override val exportProgress: StateFlow<ExportProgressInfo> = _exportProgress.asStateFlow()

    override fun updateProgress(info: ExportProgressInfo) {
        _exportProgress.value = info
    }

    override fun setExportCompletionCallback(callback: (String?) -> Unit) {
        completionCallback = callback
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun startExport(options: ExportOptions) {
        // Cancel any ongoing export job
        currentExportJob?.cancel()

        // Start a new export job
        currentExportJob =
            scope.launch {
                try {
                    Napier.i("iOS: Starting export process")

                    // Resolve date range cutoff
                    val dateRangeCutoff =
                        when (options.dateRange) {
                            is ExportDateRange.AllTime -> null
                            is ExportDateRange.Last30Days -> ExportUserDataUseCase.resolveDateRangeCutoff("last_30_days")
                            is ExportDateRange.Last90Days -> ExportUserDataUseCase.resolveDateRangeCutoff("last_90_days")
                            is ExportDateRange.LastYear -> ExportUserDataUseCase.resolveDateRangeCutoff("last_year")
                            is ExportDateRange.Custom -> options.dateRange.start
                        }

                    exportUserDataUseCase
                        .exportUserData(
                            includeJournals = options.includeJournals,
                            includeNotes = options.includeNotes,
                            includeDrafts = options.includeDrafts,
                            includeMedia = options.includeMedia,
                            dateRangeCutoff = dateRangeCutoff,
                        ).catch { exception ->
                            Napier.e("iOS: Export failed", exception)
                            showAlert(
                                title = "Export Failed",
                                message = exception.message ?: "Unknown error occurred",
                            )
                            completionCallback?.invoke(null)
                        }.collect { progress ->
                            when (progress) {
                                is ExportProgress.Starting -> {
                                    Napier.i("iOS: Export started")
                                }

                                is ExportProgress.InProgress -> {
                                    val progressInt = (progress.percentage * 100).toInt()
                                    Napier.d("iOS: Export progress: $progressInt% - ${progress.message}")
                                }

                                is ExportProgress.Completed -> {
                                    try {
                                        val exportDirPath = createExportDirectoryPath()
                                        saveExportFiles(exportDirPath, progress.result)
                                        presentShareSheet(exportDirPath)

                                        Napier.i("iOS: Export completed and share sheet presented")
                                        completionCallback?.invoke(exportDirPath)
                                    } catch (e: Exception) {
                                        Napier.e("iOS: Failed to save or share export", e)
                                        showAlert(
                                            title = "Export Failed",
                                            message = "Could not save or share the export files: ${e.message}",
                                        )
                                        completionCallback?.invoke(null)
                                    }
                                }

                                is ExportProgress.Failed -> {
                                    Napier.e("iOS: Export failed: ${progress.reason}")
                                    showAlert(
                                        title = "Export Failed",
                                        message = progress.reason,
                                    )
                                    completionCallback?.invoke(null)
                                }
                            }
                        }
                } catch (e: Exception) {
                    Napier.e("iOS: Export process failed", e)
                    showAlert(
                        title = "Export Failed",
                        message = "Export process failed: ${e.message}",
                    )
                    completionCallback?.invoke(null)
                }
            }
    }

    override fun cancelExport() {
        currentExportJob?.cancel()
        currentExportJob = null
        completionCallback?.invoke(null)
        Napier.i("iOS: Export cancelled")
    }

    private fun createExportDirectoryPath(): String {
        val timestamp =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .let {
                    "${it.year}-${it.month.number.toString().padStart(2, '0')}-${it.day.toString().padStart(2, '0')}_" +
                        "${it.hour.toString().padStart(2, '0')}-${it.minute.toString().padStart(2, '0')}"
                }
        val basePath = NSTemporaryDirectory().trimEnd('/')
        val exportDirPath = "$basePath/logdate_export_$timestamp"
        NSFileManager.defaultManager.createDirectoryAtPath(
            exportDirPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return exportDirPath
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun saveExportFiles(
        exportDirPath: String,
        result: ExportResult,
    ) {
        writeJsonFile(exportDirPath, "metadata.json", result.metadata)
        writeJsonFile(exportDirPath, "journals.json", result.journals)
        writeJsonFile(exportDirPath, "notes.json", result.notes)
        writeJsonFile(exportDirPath, "journal_notes.json", result.journalNotes)
        writeJsonFile(exportDirPath, "drafts.json", result.drafts)
        result.mediaManifest?.let { manifest ->
            writeJsonFile(exportDirPath, "media_manifest.json", manifest)
        }

        result.mediaFiles.forEach { mediaFile ->
            copyMediaFile(exportDirPath, mediaFile)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeJsonFile(
        directoryPath: String,
        fileName: String,
        contents: String,
    ) {
        val filePath = "$directoryPath/$fileName"
        autoreleasepool {
            val nsString = NSString.create(string = contents)
            val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return@autoreleasepool
            data.writeToFile(filePath, true)
        }
    }

    private fun copyMediaFile(
        exportDirPath: String,
        mediaFile: ExportMediaFile,
    ) {
        val sourceUri = mediaFile.sourceUri
        if (sourceUri.contains("://") && !sourceUri.startsWith("file://")) {
            Napier.w("iOS: Skipping media export for unsupported URI: $sourceUri")
            return
        }

        val sourcePath = sourceUri.removePrefix("file://")
        if (sourcePath.isBlank()) {
            Napier.w("iOS: Skipping media export for empty source path")
            return
        }

        val destinationPath = "$exportDirPath/${mediaFile.exportPath}"
        ensureParentDirectory(destinationPath)

        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(sourcePath)) {
            Napier.w("iOS: Media file missing at $sourcePath")
            return
        }

        fileManager.copyItemAtPath(sourcePath, destinationPath, error = null)
    }

    private fun ensureParentDirectory(filePath: String) {
        val directoryPath = filePath.substringBeforeLast("/", missingDelimiterValue = "")
        if (directoryPath.isBlank()) {
            return
        }
        NSFileManager.defaultManager.createDirectoryAtPath(
            directoryPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    /**
     * Presents the iOS share sheet with the exported files directory.
     */
    private fun presentShareSheet(path: String) {
        val fileURL = NSURL.fileURLWithPath(path)

        val activityViewController =
            UIActivityViewController(
                activityItems = listOf(fileURL),
                applicationActivities = null,
            )

        rootViewController().presentViewController(
            viewControllerToPresent = activityViewController,
            animated = true,
            completion = null,
        )
    }

    /**
     * Shows a simple alert dialog.
     */
    private fun showAlert(
        title: String,
        message: String,
    ) {
        val alertController =
            platform.UIKit.UIAlertController.alertControllerWithTitle(
                title = title,
                message = message,
                preferredStyle = platform.UIKit.UIAlertControllerStyleAlert,
            )

        alertController.addAction(
            platform.UIKit.UIAlertAction.actionWithTitle(
                title = "OK",
                style = platform.UIKit.UIAlertActionStyleDefault,
                handler = null,
            ),
        )

        rootViewController().presentViewController(
            viewControllerToPresent = alertController,
            animated = true,
            completion = null,
        )
    }
}
