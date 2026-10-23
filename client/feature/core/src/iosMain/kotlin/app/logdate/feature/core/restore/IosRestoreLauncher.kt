package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.ArchiveMediaIntegrity
import app.logdate.client.domain.restore.ArchiveZipReader
import app.logdate.client.domain.restore.RestoreArchiveReader
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.domain.restore.copyArchiveMedia
import app.logdate.client.domain.restore.mediaIntegrity
import app.logdate.client.media.MediaManager
import app.logdate.client.util.platformIODispatcher
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.uuid.Uuid

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

    override fun completeRestore(outcome: RestoreOutcome) {
        _restoreProgress.value = RestoreProgressInfo.Idle
        completionCallback?.invoke(outcome)
        Napier.i("Restore completion received")
    }

    override fun startFileSelection() {
        currentRestoreJob?.cancel()
        val picker =
            UIDocumentPickerViewController(
                documentTypes = listOf("public.data", "public.zip-archive"),
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
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Napier.e("iOS: Restore failed")
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
            FileSystem.SYSTEM.openReadOnly(path.toPath()).use { handle ->
                val zip = ArchiveZipReader(handle)
                RestoreArchiveReader.previewJson(zip.entryNames) { readOptionalEntry(zip, it)?.decodeToString() }
            }
        } catch (e: Exception) {
            Napier.e("iOS: Failed to extract metadata")
            null
        }

    private suspend fun restoreFromZip(
        path: String,
        options: ImportOptions,
    ): RestoreSummary =
        withContext(platformIODispatcher) {
            updateProgress(RestoreStage.PREPARING.toProgressInfo())
            updateProgress(RestoreStage.OPENING_ARCHIVE.toProgressInfo())

            FileSystem.SYSTEM.openReadOnly(path.toPath()).use { handle ->
                val zipFileSystem = ArchiveZipReader(handle)
                updateProgress(RestoreStage.READING_CONTENTS.toProgressInfo())
                val archive =
                    RestoreArchiveReader.readBytes(zipFileSystem.entryNames) { entryName ->
                        readOptionalEntry(zipFileSystem, entryName)
                    }
                val root = archive.root

                val mediaImporter =
                    createArchiveMediaImporter(
                        mediaManager = mediaManager,
                        references = getKoin().get(),
                        contains = { exportPath -> zipFileSystem.containsFile(root + exportPath.trimStart('/')) },
                        importFile = { exportPath -> importMedia(zipFileSystem, root, exportPath, archive.mediaIntegrity) },
                    )

                val restoreOptions =
                    RestoreOptions(
                        includeDrafts = options.includeDrafts,
                        includeMedia = options.includeMedia,
                    )

                val result =
                    restoreUserDataUseCase.restore(
                        archive = archive,
                        options = restoreOptions,
                        mediaImporter = mediaImporter,
                        onProgress = { phase -> updateProgress(phase.toProgressInfo()) },
                    )
                result.toSummary(source = path.substringAfterLast('/'))
            }
        }

    private fun readOptionalEntry(
        zipFileSystem: ArchiveZipReader,
        entryName: String,
    ): ByteArray? {
        if (!zipFileSystem.containsFile(entryName)) return null
        return zipFileSystem.source(entryName).buffer().use { it.readByteArray() }
    }

    private suspend fun importMedia(
        zipFileSystem: ArchiveZipReader,
        root: String,
        exportPath: String,
        integrity: Map<String, ArchiveMediaIntegrity>?,
    ): String? {
        val normalizedPath = exportPath.trimStart('/')
        val entryPath = root + normalizedPath
        if (!zipFileSystem.containsFile(entryPath)) {
            Napier.w("Restore attachment missing")
            return null
        }
        val temporary = (NSTemporaryDirectory() + "logdate-restore-media-${Uuid.random()}.partial").toPath()
        return try {
            zipFileSystem.source(entryPath).use { source ->
                FileSystem.SYSTEM.sink(temporary, mustCreate = true).use { destination ->
                    copyArchiveMedia(source, destination, normalizedPath, integrity)
                }
            }
            mediaManager.saveMediaFromFile(temporary.toString(), normalizedPath.substringAfterLast('/'), "application/octet-stream")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Napier.e("Restore attachment import failed")
            null
        } finally {
            runCatching { FileSystem.SYSTEM.delete(temporary, mustExist = false) }
                .onFailure { Napier.w("Could not remove temporary restore attachment") }
        }
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
