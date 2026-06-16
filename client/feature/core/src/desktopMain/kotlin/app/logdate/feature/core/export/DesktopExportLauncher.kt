package app.logdate.feature.core.export

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.domain.backup.BackupProgress
import app.logdate.client.domain.backup.CreateEncryptedBackupUseCase
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportIssue
import app.logdate.client.domain.export.ExportIssueCode
import app.logdate.client.domain.export.ExportMediaFile
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
import okio.Path.Companion.toPath
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
    private data class MediaEntryOutcome(
        val written: Boolean,
        val issue: ExportIssue? = null,
    )

    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val createEncryptedBackupUseCase: CreateEncryptedBackupUseCase by inject()
    private val identityKeyManager: IdentityKeyManager by inject()
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
                    val defaultFileName =
                        when (options.archiveFormat) {
                            ExportArchiveFormat.EncryptedBackup -> generateEncryptedBackupFileName()
                            ExportArchiveFormat.LegacyZip -> generateExportFileName()
                        }

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

                    if (options.archiveFormat == ExportArchiveFormat.EncryptedBackup) {
                        createEncryptedBackup(selectedFile)
                        return@launch
                    }

                    // Resolve date range cutoff
                    val dateRangeCutoff = options.dateRange.toCutoffInstant()

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
                            showExportErrorDialog("Export could not be completed.")
                            completionCallback?.invoke(null)
                        }.collect { progress ->
                            when (progress) {
                                is ExportProgress.Starting -> {
                                    Napier.i("Desktop: Export started")
                                }

                                is ExportProgress.InProgress -> {
                                    val progressInt = (progress.percentage * 100).toInt()
                                    Napier.d("Desktop: Export progress: $progressInt% - ${progress.stage.defaultMessage}")
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
                                        showExportErrorDialog("Could not write the export archive.")
                                        completionCallback?.invoke(null)
                                    }
                                }

                                is ExportProgress.Failed -> {
                                    val errorMessage = progress.error.defaultMessage
                                    Napier.e("Desktop: Export failed: $errorMessage")
                                    showExportErrorDialog(errorMessage)
                                    completionCallback?.invoke(null)
                                }
                            }
                        }
                } catch (e: Exception) {
                    Napier.e("Desktop: Export process failed", e)
                    showExportErrorDialog("Export could not be completed.")
                    completionCallback?.invoke(null)
                }
            }
    }

    private suspend fun createEncryptedBackup(file: File) {
        val recoveryPhrase =
            identityKeyManager.getStoredRecoveryPhrase()?.words
                ?: throw IllegalStateException(
                    "Recovery phrase is not available. Complete recovery phrase setup before creating an encrypted backup.",
                )
        val backupFile =
            if (!file.name.endsWith(".${app.logdate.client.domain.export.ExportFormat.ENCRYPTED_BACKUP_FILE_EXTENSION}")) {
                File(file.absolutePath + ".${app.logdate.client.domain.export.ExportFormat.ENCRYPTED_BACKUP_FILE_EXTENSION}")
            } else {
                file
            }
        createEncryptedBackupUseCase(backupFile.absolutePath.toPath(), recoveryPhrase)
            .collect { progress ->
                when (progress) {
                    BackupProgress.Starting ->
                        updateProgress(ExportProgressInfo(isActive = true, progressPercent = 0, message = "Starting encrypted backup"))
                    BackupProgress.ExportingData ->
                        updateProgress(ExportProgressInfo(isActive = true, progressPercent = 20, message = "Collecting backup data"))
                    BackupProgress.DerivingKeys ->
                        updateProgress(ExportProgressInfo(isActive = true, progressPercent = 45, message = "Preparing encryption"))
                    BackupProgress.Encrypting ->
                        updateProgress(ExportProgressInfo(isActive = true, progressPercent = 70, message = "Encrypting backup"))
                    is BackupProgress.Completed -> {
                        updateProgress(
                            ExportProgressInfo(
                                isActive = false,
                                progressPercent = 100,
                                message = "Encrypted backup completed",
                                completedFilePath = backupFile.absolutePath,
                            ),
                        )
                        completionCallback?.invoke(backupFile.absolutePath)
                    }
                    is BackupProgress.Failed -> throw IllegalStateException(progress.error)
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

            val exportedMediaFiles = mutableListOf<ExportMediaFile>()
            val archiveIssues = mutableListOf<ExportIssue>()
            exportResult.mediaFiles.forEach { mediaFile ->
                val outcome = addMediaEntry(zipOutputStream, mediaFile)
                if (outcome.written) {
                    exportedMediaFiles += mediaFile
                }
                outcome.issue?.let(archiveIssues::add)
            }

            exportResult.serializeMediaManifest(exportedMediaFiles)?.let { manifest ->
                addZipEntry(zipOutputStream, ExportFileStructure.MEDIA_MANIFEST_FILE, manifest)
            }
            exportResult.renderIssuesText(archiveIssues)?.let { addZipEntry(zipOutputStream, ExportFileStructure.EXPORT_ISSUES_FILE, it) }
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
        mediaFile: ExportMediaFile,
    ): MediaEntryOutcome {
        var entryOpened = false
        try {
            val sourceUri = mediaFile.sourceUri
            when {
                sourceUri.startsWith("/") || sourceUri.startsWith("file://") -> {
                    val requestedFile =
                        if (sourceUri.startsWith("file://")) {
                            File(URI(sourceUri))
                        } else {
                            File(sourceUri)
                        }
                    val file = normalizeDuplicateExtension(requestedFile)
                    if (!file.exists()) {
                        Napier.w("Desktop: Media file missing during export, skipping: ${requestedFile.absolutePath}")
                        return MediaEntryOutcome(
                            written = false,
                            issue =
                                ExportIssue(
                                    code = ExportIssueCode.MEDIA_BYTES_MISSING,
                                    source = sourceUri,
                                ),
                        )
                    }
                    zipOutputStream.putNextEntry(ZipEntry(mediaFile.exportPath))
                    entryOpened = true
                    file.inputStream().use { it.copyTo(zipOutputStream) }
                    zipOutputStream.closeEntry()
                    entryOpened = false
                    return MediaEntryOutcome(
                        written = true,
                        issue =
                            if (file.absolutePath != requestedFile.absolutePath) {
                                ExportIssue(
                                    code = ExportIssueCode.MEDIA_RECOVERED_NORMALIZED_PATH,
                                    source = sourceUri,
                                )
                            } else {
                                null
                            },
                    )
                }
                else -> {
                    zipOutputStream.putNextEntry(ZipEntry(mediaFile.exportPath))
                    entryOpened = true
                    URI(sourceUri).toURL().openStream().use { it.copyTo(zipOutputStream) }
                    zipOutputStream.closeEntry()
                    entryOpened = false
                    return MediaEntryOutcome(written = true)
                }
            }
        } catch (e: Exception) {
            if (entryOpened) {
                runCatching { zipOutputStream.closeEntry() }
            }
            Napier.w("Desktop: Failed to add media file to ZIP, skipping: ${mediaFile.sourceUri}", e)
            return MediaEntryOutcome(
                written = false,
                issue =
                    ExportIssue(
                        code = ExportIssueCode.MEDIA_BYTES_MISSING,
                        source = mediaFile.sourceUri,
                    ),
            )
        }
    }

    private fun normalizeDuplicateExtension(file: File): File {
        val normalizedName =
            file.name.replace(Regex("(\\.[A-Za-z0-9]+)\\1$")) { match ->
                match.groupValues[1]
            }
        return if (normalizedName == file.name) file else File(file.parentFile, normalizedName)
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
