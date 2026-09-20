package app.logdate.feature.core.export

import app.logdate.client.domain.export.archive.ArchiveExportOptions
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ExportArchiveUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** Desktop-specific data export using an AWT save dialog. */
class DesktopExportLauncher :
    ExportLauncher,
    KoinComponent {
    private val exportArchiveUseCase: ExportArchiveUseCase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
                try {
                    val fileDialog = createSaveDialog(generateExportFileName())
                    val selectedFile =
                        fileDialog.directory?.let { directory ->
                            val fileName = fileDialog.file ?: return@let null
                            File(directory, fileName)
                        }

                    if (selectedFile == null) {
                        Napier.i("Desktop: Export cancelled by user")
                        completionCallback?.invoke(ExportOutcome.Cancelled)
                        return@launch
                    }

                    Napier.i("Desktop: Starting export to ${selectedFile.absolutePath}")
                    runArchiveExport(selectedFile, options)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    Napier.e("Desktop: Export process failed", failure)
                    showExportErrorDialog("Export could not be completed.")
                    completionCallback?.invoke(ExportOutcome.Failed("Export could not be completed."))
                }
            }
    }

    /**
     * Writes a v2 archive to the chosen file. The file is replaced only after the archive is
     * complete, so a failed or cancelled export never damages an existing file.
     */
    private suspend fun runArchiveExport(
        selectedFile: File,
        options: ExportOptions,
    ) {
        val zipFile = if (selectedFile.name.endsWith(".zip")) selectedFile else File(selectedFile.absolutePath + ".zip")
        val archiveOptions =
            ArchiveExportOptions(
                includeJournals = options.includeJournals,
                includeNotes = options.includeNotes,
                includeDrafts = options.includeDrafts,
                includeMedia = options.includeMedia,
                from = options.dateRange.toCutoffInstant(),
            )
        var completed = false
        try {
            val outcome =
                try {
                    exportArchiveToFile(zipFile, { exportArchiveUseCase.export(archiveOptions, it) }, ::publishArchiveProgress)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    Napier.e("Desktop: Archive export failed", failure)
                    ArchiveFileOutcome.Failed("Could not write the export archive.")
                }
            when (outcome) {
                is ArchiveFileOutcome.Failed -> {
                    updateProgress(ExportProgressInfo())
                    showExportErrorDialog(outcome.message)
                    completionCallback?.invoke(ExportOutcome.Failed(outcome.message))
                }
                is ArchiveFileOutcome.Completed -> {
                    completed = true
                    Napier.i("Desktop: Archive export completed to ${zipFile.absolutePath}")
                    showExportSuccessDialog(zipFile.absolutePath)
                    updateProgress(
                        ExportProgressInfo(
                            isActive = false,
                            progressPercent = 100,
                            message = "Export completed",
                            completedFilePath = zipFile.absolutePath,
                            stats = outcome.summary.counts.toExportStats(),
                        ),
                    )
                }
            }
        } finally {
            if (!completed) updateProgress(ExportProgressInfo())
        }
    }

    private fun publishArchiveProgress(progress: ArchiveExportProgress.InProgress) =
        updateProgress(
            ExportProgressInfo(
                isActive = true,
                progressPercent = (progress.fraction * 100).toInt(),
                message = progress.stage.defaultMessage,
            ),
        )

    override fun cancelExport() {
        currentExportJob?.cancel()
        currentExportJob = null
        completionCallback?.invoke(ExportOutcome.Cancelled)
        Napier.i("Desktop: Export cancelled")
    }

    private fun createSaveDialog(defaultFileName: String): FileDialog =
        FileDialog(null as Frame?, "Save LogDate Export", FileDialog.SAVE).apply {
            file = defaultFileName
            isVisible = true
        }

    private fun showExportSuccessDialog(filePath: String) {
        Napier.i("Desktop: Export completed successfully to $filePath")
        try {
            val dialog = java.awt.Dialog(null as Frame?, "Export Successful", true)
            dialog.layout = java.awt.BorderLayout()
            dialog.add(
                java.awt.Label("Export completed successfully to $filePath"),
                java.awt.BorderLayout.CENTER,
            )
            val closeButton = java.awt.Button("OK")
            closeButton.addActionListener { dialog.dispose() }
            dialog.add(closeButton, java.awt.BorderLayout.SOUTH)
            dialog.setBounds(100, 100, 400, 100)
            dialog.isVisible = true
        } catch (failure: Exception) {
            Napier.e("Desktop: Failed to show success dialog", failure)
        }
    }

    private fun showExportErrorDialog(errorMessage: String) {
        Napier.e("Desktop: Export failed: $errorMessage")
        try {
            val dialog = java.awt.Dialog(null as Frame?, "Export Failed", true)
            dialog.layout = java.awt.BorderLayout()
            dialog.add(
                java.awt.Label("Export failed: $errorMessage"),
                java.awt.BorderLayout.CENTER,
            )
            val closeButton = java.awt.Button("OK")
            closeButton.addActionListener { dialog.dispose() }
            dialog.add(closeButton, java.awt.BorderLayout.SOUTH)
            dialog.setBounds(100, 100, 400, 100)
            dialog.isVisible = true
        } catch (failure: Exception) {
            Napier.e("Desktop: Failed to show error dialog", failure)
        }
    }
}
