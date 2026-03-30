@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package app.logdate.feature.core.export

import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.client.domain.export.ZipArchiveEntry
import app.logdate.client.domain.export.ZipArchiveWriter
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController

/**
 * iOS-specific implementation for launching data export.
 *
 * This implementation creates a temporary ZIP archive and presents the iOS share sheet
 * to let the user save or share it.
 */
@OptIn(ExperimentalForeignApi::class)
class IosExportLauncher(
    private val rootViewController: () -> UIViewController,
) : ExportLauncher,
    KoinComponent {
    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val zipArchiveWriter = ZipArchiveWriter()
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
                                        val exportFilePath = createExportFilePath()
                                        saveExportArchive(exportFilePath, progress.result)
                                        presentShareSheet(exportFilePath)

                                        Napier.i("iOS: Export completed and share sheet presented")
                                        updateProgress(
                                            ExportProgressInfo(
                                                isActive = false,
                                                progressPercent = 100,
                                                message = "Export completed",
                                                completedFilePath = exportFilePath,
                                                stats = progress.result.stats,
                                            ),
                                        )
                                        completionCallback?.invoke(exportFilePath)
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

    private fun createExportFilePath(): String {
        val basePath = NSTemporaryDirectory().trimEnd('/')
        return "$basePath/${generateExportFileName()}"
    }

    private fun saveExportArchive(
        exportFilePath: String,
        result: ExportResult,
    ) {
        val entries =
            buildList {
                addJsonEntry(ExportFileStructure.METADATA_FILE, result.metadata)
                addJsonEntry(ExportFileStructure.JOURNALS_FILE, result.journals)
                addJsonEntry(ExportFileStructure.NOTES_FILE, result.notes)
                addJsonEntry(ExportFileStructure.JOURNAL_NOTES_FILE, result.journalNotes)
                addJsonEntry(ExportFileStructure.DRAFTS_FILE, result.drafts)
                result.profile?.let { addJsonEntry(ExportFileStructure.PROFILE_FILE, it) }
                result.places?.let { addJsonEntry(ExportFileStructure.PLACES_FILE, it) }
                result.locationHistory?.let { addJsonEntry(ExportFileStructure.LOCATION_HISTORY_FILE, it) }
                result.mediaManifest?.let { addJsonEntry(ExportFileStructure.MEDIA_MANIFEST_FILE, it) }
                result.mediaFiles.forEach { mediaFile ->
                    add(buildMediaEntry(mediaFile))
                }
            }

        zipArchiveWriter.write(exportFilePath.toPath(), entries)
    }

    private fun MutableList<ZipArchiveEntry>.addJsonEntry(
        fileName: String,
        contents: String,
    ) {
        add(
            ZipArchiveEntry.Bytes(
                path = fileName,
                bytes = contents.encodeToByteArray(),
            ),
        )
    }

    private fun buildMediaEntry(mediaFile: ExportMediaFile): ZipArchiveEntry.File {
        val sourceUri = mediaFile.sourceUri
        val sourcePath =
            when {
                sourceUri.startsWith("file://") -> sourceUri.removePrefix("file://")
                sourceUri.startsWith("/") -> sourceUri
                else -> throw IllegalStateException("Unsupported media URI for iOS export: $sourceUri")
            }

        require(sourcePath.isNotBlank()) {
            "Media source path is empty for export entry ${mediaFile.exportPath}"
        }

        if (!NSFileManager.defaultManager.fileExistsAtPath(sourcePath)) {
            throw IllegalStateException("Media file missing at $sourcePath")
        }

        return ZipArchiveEntry.File(
            path = mediaFile.exportPath,
            sourcePath = sourcePath.toPath(),
        )
    }

    /**
     * Presents the iOS share sheet with the exported ZIP archive.
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
