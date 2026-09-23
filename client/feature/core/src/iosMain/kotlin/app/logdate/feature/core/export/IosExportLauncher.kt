@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package app.logdate.feature.core.export

import app.logdate.client.domain.export.archive.ArchiveExportOptions
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ExportArchiveUseCase
import app.logdate.client.domain.export.archive.StagingZipArchiveContainer
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import kotlin.coroutines.cancellation.CancellationException

/** Creates a portable v2 archive and presents the finished file through the iOS share sheet. */
@OptIn(ExperimentalForeignApi::class)
class IosExportLauncher(
    private val rootViewController: () -> UIViewController,
) : ExportLauncher,
    KoinComponent {
    private val exportArchiveUseCase: ExportArchiveUseCase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentExportJob: Job? = null
    private var completionCallback: ((ExportOutcome) -> Unit)? = null

    private val _exportProgress = MutableStateFlow(ExportProgressInfo())
    override val exportProgress: StateFlow<ExportProgressInfo> = _exportProgress.asStateFlow()

    override fun updateProgress(info: ExportProgressInfo) {
        _exportProgress.value = info
    }

    override fun setExportCompletionCallback(callback: (ExportOutcome) -> Unit) {
        completionCallback = callback
    }

    override fun startExport(options: ExportOptions) {
        currentExportJob?.cancel()
        currentExportJob =
            scope.launch {
                val exportFilePath = createExportFilePath()
                val outputPath = exportFilePath.toPath()
                val container =
                    StagingZipArchiveContainer(
                        fileSystem = FileSystem.SYSTEM,
                        stagingDirectory = "$exportFilePath.staging".toPath(),
                    )
                var archiveFinished = false
                try {
                    exportArchiveUseCase
                        .export(options.toArchiveExportOptions(), container)
                        .collect { progress ->
                            archiveFinished =
                                handleExportProgress(progress, exportFilePath, outputPath, container, archiveFinished)
                        }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    Napier.e("iOS: Export process failed", failure)
                    showAlert("Export Failed", "Could not write the export archive.")
                    completionCallback?.invoke(ExportOutcome.Failed("Could not write the export archive."))
                } finally {
                    if (!archiveFinished) {
                        container.discard()
                        FileSystem.SYSTEM.delete(outputPath, mustExist = false)
                        updateProgress(ExportProgressInfo())
                    }
                }
            }
    }

    /** Applies one [ArchiveExportProgress] update and returns whether the archive has finished writing. */
    private fun handleExportProgress(
        progress: ArchiveExportProgress,
        exportFilePath: String,
        outputPath: Path,
        container: StagingZipArchiveContainer,
        archiveFinished: Boolean,
    ): Boolean {
        when (progress) {
            ArchiveExportProgress.Starting -> {
                updateProgress(ExportProgressInfo(isActive = true, message = "Preparing export..."))
            }

            is ArchiveExportProgress.InProgress -> {
                updateProgress(
                    ExportProgressInfo(
                        isActive = true,
                        progressPercent = (progress.fraction * 100).toInt(),
                        message = progress.stage.defaultMessage,
                    ),
                )
            }

            is ArchiveExportProgress.Completed -> {
                container.finish(outputPath)
                presentShareSheet(exportFilePath)
                updateProgress(
                    ExportProgressInfo(
                        isActive = false,
                        progressPercent = 100,
                        message = "Export completed",
                        completedFilePath = exportFilePath,
                        stats = progress.summary.counts.toExportStats(),
                    ),
                )
                return true
            }

            is ArchiveExportProgress.Failed -> {
                val message = progress.error.defaultMessage
                showAlert("Export Failed", message)
                completionCallback?.invoke(ExportOutcome.Failed(message))
            }
        }
        return archiveFinished
    }

    override fun cancelExport() {
        currentExportJob?.cancel()
        currentExportJob = null
        completionCallback?.invoke(ExportOutcome.Cancelled)
        Napier.i("iOS: Export cancelled")
    }

    private fun ExportOptions.toArchiveExportOptions(): ArchiveExportOptions =
        ArchiveExportOptions(
            includeJournals = includeJournals,
            includeNotes = includeNotes,
            includeDrafts = includeDrafts,
            includeMedia = includeMedia,
            from = dateRange.toCutoffInstant(),
            to = (dateRange as? ExportDateRange.Custom)?.end,
        )

    private fun createExportFilePath(): String = "${NSTemporaryDirectory().trimEnd('/')}/${generateExportFileName()}"

    private fun presentShareSheet(path: String) {
        val activityViewController =
            UIActivityViewController(
                activityItems = listOf(NSURL.fileURLWithPath(path)),
                applicationActivities = null,
            )
        rootViewController().presentViewController(activityViewController, animated = true, completion = null)
    }

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
        rootViewController().presentViewController(alertController, animated = true, completion = null)
    }
}
