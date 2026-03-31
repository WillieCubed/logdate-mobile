package app.logdate.feature.core.export

import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportUserDataUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Desktop-specific implementation for launching data export using AWT FileDialog.
 */
class DesktopExportLauncher :
    ExportLauncher,
    KoinComponent {
    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    override fun startExport(options: ExportOptions) {
        // Cancel any existing export job
        currentExportJob?.cancel()

        // Start a new export job
        currentExportJob =
            scope.launch {
                try {
                    val defaultFileName = generateExportFileName()

                    // Show file save dialog on the main thread
                    val fileDialog = createSaveDialog(defaultFileName)
                    val selectedFile =
                        fileDialog.directory?.let { dir ->
                            val fileName = fileDialog.file ?: return@let null
                            File(dir, fileName)
                        }

                    if (selectedFile == null) {
                        Napier.i("Desktop: Export cancelled by user")
                        completionCallback?.invoke(null)
                        return@launch
                    }

                    Napier.i("Desktop: Starting export to ${selectedFile.absolutePath}")

                    // Resolve date range cutoff
                    val dateRangeCutoff =
                        when (options.dateRange) {
                            is ExportDateRange.AllTime -> null
                            is ExportDateRange.Last30Days -> ExportUserDataUseCase.resolveDateRangeCutoff("last_30_days")
                            is ExportDateRange.Last90Days -> ExportUserDataUseCase.resolveDateRangeCutoff("last_90_days")
                            is ExportDateRange.LastYear -> ExportUserDataUseCase.resolveDateRangeCutoff("last_year")
                            is ExportDateRange.Custom -> options.dateRange.start
                        }

                    // Collect and save the data
                    exportUserDataUseCase
                        .exportUserData(
                            includeJournals = options.includeJournals,
                            includeNotes = options.includeNotes,
                            includeDrafts = options.includeDrafts,
                            includeMedia = options.includeMedia,
                            dateRangeCutoff = dateRangeCutoff,
                        ).catch { exception ->
                            Napier.e("Desktop: Export failed", exception)
                            showExportErrorDialog("Export failed: ${exception.message}")
                            completionCallback?.invoke(null)
                        }.collect { progress ->
                            when (progress) {
                                is ExportProgress.Starting -> {
                                    Napier.i("Desktop: Export started")
                                }

                                is ExportProgress.InProgress -> {
                                    val progressInt = (progress.percentage * 100).toInt()
                                    Napier.d("Desktop: Export progress: $progressInt% - ${progress.message}")
                                }

                                is ExportProgress.Completed -> {
                                    try {
                                        // Save the entire export result as a zip file
                                        saveToFile(selectedFile, progress.result)
                                        val absolutePath = selectedFile.absolutePath
                                        val zipPath =
                                            if (!absolutePath.endsWith(".zip")) {
                                                "$absolutePath.zip"
                                            } else {
                                                absolutePath
                                            }
                                        Napier.i("Desktop: Export completed successfully to $zipPath")
                                        showExportSuccessDialog(zipPath)
                                        updateProgress(
                                            ExportProgressInfo(
                                                isActive = false,
                                                progressPercent = 100,
                                                message = "Export completed",
                                                completedFilePath = zipPath,
                                                stats = progress.result.stats,
                                            ),
                                        )
                                        completionCallback?.invoke(zipPath)
                                    } catch (e: Exception) {
                                        Napier.e("Desktop: Failed to save file", e)
                                        showExportErrorDialog("Failed to save file: ${e.message}")
                                        completionCallback?.invoke(null)
                                    }
                                }

                                is ExportProgress.Failed -> {
                                    Napier.e("Desktop: Export failed: ${progress.reason}")
                                    showExportErrorDialog(progress.reason)
                                    completionCallback?.invoke(null)
                                }
                            }
                        }
                } catch (e: Exception) {
                    Napier.e("Desktop: Export process failed", e)
                    showExportErrorDialog("Export process failed: ${e.message}")
                    completionCallback?.invoke(null)
                }
            }
    }

    override fun cancelExport() {
        currentExportJob?.cancel()
        currentExportJob = null
        completionCallback?.invoke(null)
        Napier.i("Desktop: Export cancelled")
    }

    private fun createSaveDialog(defaultFileName: String): FileDialog =
        FileDialog(null as Frame?, "Save LogDate Export", FileDialog.SAVE).apply {
            file = defaultFileName
            isVisible = true // This blocks until user selects a file or cancels
        }

    private fun saveToFile(
        file: File,
        exportResult: ExportResult,
    ) {
        // Create a zip file
        val zipFile =
            if (!file.name.endsWith(".zip")) {
                File(file.absolutePath + ".zip")
            } else {
                file
            }

        Napier.i("Desktop: Creating zip file at ${zipFile.absolutePath}")

        // Create zip output stream
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOutputStream ->
            addZipEntry(zipOutputStream, ExportFileStructure.METADATA_FILE, exportResult.serializeMetadata())
            addZipEntry(zipOutputStream, ExportFileStructure.JOURNALS_FILE, exportResult.serializeJournals())
            addZipEntry(zipOutputStream, ExportFileStructure.NOTES_FILE, exportResult.serializeNotes())
            addZipEntry(zipOutputStream, ExportFileStructure.JOURNAL_NOTES_FILE, exportResult.serializeJournalNotes())
            addZipEntry(zipOutputStream, ExportFileStructure.DRAFTS_FILE, exportResult.serializeDrafts())
            exportResult.serializeProfile()?.let { addZipEntry(zipOutputStream, ExportFileStructure.PROFILE_FILE, it) }
            exportResult.serializePlaces()?.let { addZipEntry(zipOutputStream, ExportFileStructure.PLACES_FILE, it) }
            exportResult.serializeLocationHistory()?.let { addZipEntry(zipOutputStream, ExportFileStructure.LOCATION_HISTORY_FILE, it) }

            exportResult.serializeMediaManifest()?.let { manifest ->
                addZipEntry(zipOutputStream, ExportFileStructure.MEDIA_MANIFEST_FILE, manifest)
            }

            exportResult.mediaFiles.forEach { mediaFile ->
                addMediaEntry(zipOutputStream, mediaFile)
            }
        }
    }

    private fun addZipEntry(
        zipOutputStream: ZipOutputStream,
        entryName: String,
        content: String,
    ) {
        val entry = ZipEntry(entryName)
        zipOutputStream.putNextEntry(entry)
        zipOutputStream.write(content.toByteArray())
        zipOutputStream.closeEntry()
    }

    private fun addMediaEntry(
        zipOutputStream: ZipOutputStream,
        mediaFile: app.logdate.client.domain.export.ExportMediaFile,
    ) {
        zipOutputStream.putNextEntry(ZipEntry(mediaFile.exportPath))
        try {
            val sourceUri = mediaFile.sourceUri
            when {
                sourceUri.startsWith("/") || sourceUri.startsWith("file://") -> {
                    val file =
                        if (sourceUri.startsWith("file://")) {
                            File(URI(sourceUri))
                        } else {
                            File(sourceUri)
                        }
                    require(file.exists()) { "Media file missing at ${file.absolutePath}" }
                    file.inputStream().use { it.copyTo(zipOutputStream) }
                }
                else -> {
                    URI(sourceUri).toURL().openStream().use { it.copyTo(zipOutputStream) }
                }
            }
        } catch (e: Exception) {
            Napier.e("Desktop: Failed to add media file to ZIP: ${mediaFile.sourceUri}", e)
            throw IllegalStateException("Failed to include media file: ${mediaFile.sourceUri}", e)
        } finally {
            zipOutputStream.closeEntry()
        }
    }

    private fun showExportSuccessDialog(filePath: String) {
        // In a real implementation, this would show a desktop notification or dialog
        Napier.i("Desktop: Export completed successfully to $filePath")

        // For a basic implementation we could use a simple dialog
        // This implementation uses java.awt which might not be available in all contexts
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
        } catch (e: Exception) {
            Napier.e("Desktop: Failed to show success dialog", e)
        }
    }

    private fun showExportErrorDialog(errorMessage: String) {
        // In a real implementation, this would show a desktop notification or dialog
        Napier.e("Desktop: Export failed: $errorMessage")

        // For a basic implementation we could use a simple dialog
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
        } catch (e: Exception) {
            Napier.e("Desktop: Failed to show error dialog", e)
        }
    }
}
