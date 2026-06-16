@file:OptIn(BetaInteropApi::class)

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
import app.logdate.client.domain.export.ZipArchiveEntry
import app.logdate.client.domain.export.ZipArchiveWriter
import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
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
import okio.FileSystem
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
    private data class MediaArchiveEntry(
        val entry: ZipArchiveEntry.File? = null,
        val issue: ExportIssue? = null,
    )

    private val createEncryptedBackupUseCase: CreateEncryptedBackupUseCase by inject()
    private val identityKeyManager: IdentityKeyManager by inject()
    private val exportUserDataUseCase: ExportUserDataUseCase by inject()
    private val zipArchiveWriter = ZipArchiveWriter(FileSystem.SYSTEM)
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

                    if (options.archiveFormat == ExportArchiveFormat.EncryptedBackup) {
                        createEncryptedBackup()
                        return@launch
                    }

                    // Resolve date range cutoff
                    val dateRangeCutoff = options.dateRange.toCutoffInstant()

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
                                message = "Export could not be completed.",
                            )
                            completionCallback?.invoke(null)
                        }.collect { progress ->
                            when (progress) {
                                is ExportProgress.Starting -> {
                                    Napier.i("iOS: Export started")
                                }

                                is ExportProgress.InProgress -> {
                                    val progressInt = (progress.percentage * 100).toInt()
                                    Napier.d("iOS: Export progress: $progressInt% - ${progress.stage.defaultMessage}")
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
                                            message = "Could not write the export archive.",
                                        )
                                        completionCallback?.invoke(null)
                                    }
                                }

                                is ExportProgress.Failed -> {
                                    val errorMessage = progress.error.defaultMessage
                                    Napier.e("iOS: Export failed: $errorMessage")
                                    showAlert(
                                        title = "Export Failed",
                                        message = errorMessage,
                                    )
                                    completionCallback?.invoke(null)
                                }
                            }
                        }
                } catch (e: Exception) {
                    Napier.e("iOS: Export process failed", e)
                    showAlert(
                        title = "Export Failed",
                        message = "Export could not be completed.",
                    )
                    completionCallback?.invoke(null)
                }
            }
    }

    private suspend fun createEncryptedBackup() {
        val recoveryPhrase =
            identityKeyManager.getStoredRecoveryPhrase()?.words
                ?: throw IllegalStateException(
                    "Recovery phrase is not available. Complete recovery phrase setup before creating an encrypted backup.",
                )
        val backupPath = createEncryptedBackupFilePath()
        createEncryptedBackupUseCase(backupPath.toPath(), recoveryPhrase)
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
                        presentShareSheet(backupPath)
                        updateProgress(
                            ExportProgressInfo(
                                isActive = false,
                                progressPercent = 100,
                                message = "Encrypted backup completed",
                                completedFilePath = backupPath,
                            ),
                        )
                        completionCallback?.invoke(backupPath)
                    }
                    is BackupProgress.Failed -> throw IllegalStateException(progress.error)
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

    private fun createEncryptedBackupFilePath(): String {
        val basePath = NSTemporaryDirectory().trimEnd('/')
        return "$basePath/${generateEncryptedBackupFileName()}"
    }

    private fun saveExportArchive(
        exportFilePath: String,
        result: ExportResult,
    ) {
        val entries =
            buildList {
                addJsonEntry(ExportFileStructure.METADATA_FILE, result.serializeMetadata())
                addJsonEntry(ExportFileStructure.JOURNALS_FILE, result.serializeJournals())
                addJsonEntry(ExportFileStructure.NOTES_FILE, result.serializeNotes())
                addJsonEntry(ExportFileStructure.JOURNAL_NOTES_FILE, result.serializeJournalNotes())
                addJsonEntry(ExportFileStructure.DRAFTS_FILE, result.serializeDrafts())
                result.serializeProfile()?.let { addJsonEntry(ExportFileStructure.PROFILE_FILE, it) }
                result.serializePlaces()?.let { addJsonEntry(ExportFileStructure.PLACES_FILE, it) }
                result.serializeLocationHistory()?.let { addJsonEntry(ExportFileStructure.LOCATION_HISTORY_FILE, it) }
                val exportedMediaFiles = mutableListOf<ExportMediaFile>()
                val archiveIssues = mutableListOf<ExportIssue>()
                result.mediaFiles.forEach { mediaFile ->
                    val mediaEntry = buildMediaEntry(mediaFile)
                    mediaEntry.entry?.let {
                        exportedMediaFiles += mediaFile
                        add(it)
                    }
                    mediaEntry.issue?.let(archiveIssues::add)
                }
                result.serializeMediaManifest(exportedMediaFiles)?.let { addJsonEntry(ExportFileStructure.MEDIA_MANIFEST_FILE, it) }
                result.renderIssuesText(archiveIssues)?.let { addJsonEntry(ExportFileStructure.EXPORT_ISSUES_FILE, it) }
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

    private fun buildMediaEntry(mediaFile: ExportMediaFile): MediaArchiveEntry {
        val sourceUri = mediaFile.sourceUri
        val sourcePath =
            when {
                sourceUri.startsWith("file://") -> sourceUri.removePrefix("file://")
                sourceUri.startsWith("/") -> sourceUri
                else -> {
                    Napier.w("iOS: Unsupported media URI for export, skipping: $sourceUri")
                    return MediaArchiveEntry(
                        issue =
                            ExportIssue(
                                code = ExportIssueCode.MEDIA_BYTES_MISSING,
                                source = sourceUri,
                            ),
                    )
                }
            }

        require(sourcePath.isNotBlank()) {
            "Media source path is empty for export entry ${mediaFile.exportPath}"
        }

        val normalizedSourcePath = normalizeDuplicateExtensionPath(sourcePath)
        if (!NSFileManager.defaultManager.fileExistsAtPath(normalizedSourcePath)) {
            Napier.w("iOS: Media file missing during export, skipping: $sourcePath")
            return MediaArchiveEntry(
                issue =
                    ExportIssue(
                        code = ExportIssueCode.MEDIA_BYTES_MISSING,
                        source = sourceUri,
                    ),
            )
        }

        return MediaArchiveEntry(
            entry =
                ZipArchiveEntry.File(
                    path = mediaFile.exportPath,
                    sourcePath = normalizedSourcePath.toPath(),
                ),
            issue =
                if (normalizedSourcePath != sourcePath) {
                    ExportIssue(
                        code = ExportIssueCode.MEDIA_RECOVERED_NORMALIZED_PATH,
                        source = sourceUri,
                    )
                } else {
                    null
                },
        )
    }

    private fun normalizeDuplicateExtensionPath(path: String): String =
        path.replace(Regex("(\\.[A-Za-z0-9]+)\\1$")) { match ->
            match.groupValues[1]
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
