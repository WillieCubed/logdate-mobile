package app.logdate.feature.core.restore

import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
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
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * iOS-specific implementation for restoring LogDate ZIP exports.
 */
@OptIn(ExperimentalForeignApi::class)
class IosRestoreLauncher(
    private val rootViewController: () -> UIViewController,
) : RestoreLauncher,
    KoinComponent {
    private val restoreUserDataUseCase: RestoreUserDataUseCase by inject()
    private val mediaManager: MediaManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentRestoreJob: Job? = null
    private var completionCallback: ((RestoreOutcome) -> Unit)? = null
    private var fileSelectedCallback: ((ArchiveFileInfo?) -> Unit)? = null
    private var selectedUrl: NSURL? = null

    private val _restoreProgress = MutableStateFlow<RestoreProgressInfo>(RestoreProgressInfo.Idle)
    override val restoreProgress: StateFlow<RestoreProgressInfo> = _restoreProgress.asStateFlow()

    private val documentPickerDelegate =
        RestoreDocumentPickerDelegate(
            onPick = { url ->
                if (url == null) {
                    fileSelectedCallback?.invoke(null)
                    return@RestoreDocumentPickerDelegate
                }
                handlePickedUrl(url)
            },
            onCancel = {
                fileSelectedCallback?.invoke(null)
            },
        )

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {
        completionCallback = callback
    }

    override fun setFileSelectedCallback(callback: (ArchiveFileInfo?) -> Unit) {
        fileSelectedCallback = callback
    }

    override fun updateProgress(info: RestoreProgressInfo) {
        _restoreProgress.value = info
    }

    override fun startFileSelection() {
        currentRestoreJob?.cancel()
        val picker =
            UIDocumentPickerViewController(
                documentTypes = listOf("public.zip-archive"),
                inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen,
            )
        picker.allowsMultipleSelection = false
        picker.delegate = documentPickerDelegate
        rootViewController().presentViewController(
            viewControllerToPresent = picker,
            animated = true,
            completion = null,
        )
    }

    override fun startRestore(options: ImportOptions) {
        val url =
            selectedUrl ?: run {
                completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.MISSING_SOURCE))
                return
            }

        currentRestoreJob?.cancel()
        currentRestoreJob =
            scope.launch {
                try {
                    val path =
                        url.path ?: run {
                            completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.FILE_NOT_ACCESSIBLE))
                            return@launch
                        }

                    completionCallback?.invoke(RestoreOutcome.Started)
                    _restoreProgress.value = RestoreProgressInfo.Idle

                    val summary = restoreFromZip(path, options)
                    _restoreProgress.value = RestoreProgressInfo.Idle
                    completionCallback?.invoke(RestoreOutcome.Success(summary))
                } catch (e: Exception) {
                    Napier.e("iOS: Restore failed", e)
                    _restoreProgress.value = RestoreProgressInfo.Idle
                    completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))
                }
            }
    }

    override fun cancelRestore() {
        currentRestoreJob?.cancel()
        currentRestoreJob = null
        selectedUrl = null
        _restoreProgress.value = RestoreProgressInfo.Idle
        completionCallback?.invoke(RestoreOutcome.Cancelled)
    }

    private fun handlePickedUrl(url: NSURL) {
        selectedUrl = url
        val path = url.path
        if (path == null) {
            fileSelectedCallback?.invoke(null)
            completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.FILE_NOT_ACCESSIBLE))
            return
        }

        val metadataJson = extractMetadata(path)
        if (metadataJson == null) {
            fileSelectedCallback?.invoke(null)
            completionCallback?.invoke(
                RestoreOutcome.Failure(RestoreError.INVALID_ARCHIVE),
            )
            return
        }

        val displayName = path.substringAfterLast('/')
        fileSelectedCallback?.invoke(
            ArchiveFileInfo(
                displayName = displayName,
                uri = path,
                metadataJson = metadataJson,
            ),
        )
    }

    private fun extractMetadata(path: String): String? =
        try {
            val structure = ExportFileStructure()
            val zipFileSystem = FileSystem.SYSTEM.openZip(path.toPath())
            readOptionalEntry(zipFileSystem, structure.metadataFile)
        } catch (e: Exception) {
            Napier.e("iOS: Failed to extract metadata", e)
            null
        }

    private suspend fun restoreFromZip(
        path: String,
        options: ImportOptions,
    ): RestoreSummary {
        updateProgress(RestoreStage.PREPARING.toProgressInfo())
        updateProgress(RestoreStage.OPENING_ARCHIVE.toProgressInfo())

        val structure = ExportFileStructure()
        val zipFileSystem = FileSystem.SYSTEM.openZip(path.toPath())
        updateProgress(RestoreStage.READING_CONTENTS.toProgressInfo())
        val bundle =
            RestoreBundle(
                metadataJson = readRequiredEntry(zipFileSystem, structure.metadataFile),
                journalsJson = readRequiredEntry(zipFileSystem, structure.journalsFile),
                notesJson = readRequiredEntry(zipFileSystem, structure.notesFile),
                journalNotesJson = readRequiredEntry(zipFileSystem, structure.journalNotesFile),
                draftsJson = readRequiredEntry(zipFileSystem, structure.draftsFile),
                profileJson = readOptionalEntry(zipFileSystem, structure.profileFile),
                placesJson = readOptionalEntry(zipFileSystem, structure.placesFile),
                locationHistoryJson = readOptionalEntry(zipFileSystem, structure.locationHistoryFile),
                mediaManifestJson = readOptionalEntry(zipFileSystem, structure.mediaManifestFile),
            )

        val mediaImporter =
            if (options.includeMedia) {
                object : MediaImporter {
                    override suspend fun importMedia(exportPath: String): String? =
                        this@IosRestoreLauncher.importMedia(zipFileSystem, exportPath)
                }
            } else {
                null
            }

        val restoreOptions =
            RestoreOptions(
                includeDrafts = options.includeDrafts,
                includeMedia = options.includeMedia,
            )

        val result =
            restoreUserDataUseCase.restore(
                bundle = bundle,
                options = restoreOptions,
                mediaImporter = mediaImporter,
                onProgress = { phase -> updateProgress(phase.toProgressInfo()) },
            )
        return result.toSummary(source = path.substringAfterLast('/'))
    }

    private fun readRequiredEntry(
        zipFileSystem: FileSystem,
        entryName: String,
    ): String =
        readOptionalEntry(zipFileSystem, entryName)
            ?: throw IllegalStateException("Missing required file: $entryName")

    private fun readOptionalEntry(
        zipFileSystem: FileSystem,
        entryName: String,
    ): String? {
        val entryPath = entryName.trimStart('/').toPath()
        val metadata = zipFileSystem.metadataOrNull(entryPath) ?: return null
        if (metadata.isDirectory) {
            return null
        }
        return zipFileSystem.source(entryPath).buffer().readUtf8()
    }

    private suspend fun importMedia(
        zipFileSystem: FileSystem,
        exportPath: String,
    ): String? {
        val normalizedPath = exportPath.trimStart('/')
        val entryPath = normalizedPath.toPath()
        val metadata = zipFileSystem.metadataOrNull(entryPath) ?: return null
        if (metadata.isDirectory) {
            return null
        }
        val bytes = zipFileSystem.source(entryPath).buffer().readByteArray()
        val payload =
            MediaPayload(
                fileName = normalizedPath.substringAfterLast('/'),
                mimeType = "application/octet-stream",
                sizeBytes = bytes.size.toLong(),
                data = bytes,
            )
        return runCatching { mediaManager.saveMedia(payload) }
            .onFailure { Napier.e("iOS: Failed to import zipped media", it) }
            .getOrNull()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class RestoreDocumentPickerDelegate(
    private val onPick: (platform.Foundation.NSURL?) -> Unit,
    private val onCancel: () -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? platform.Foundation.NSURL
        onPick(url)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onCancel()
    }
}
