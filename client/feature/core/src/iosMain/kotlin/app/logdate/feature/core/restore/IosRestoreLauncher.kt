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
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.Foundation.NSFileManager
import platform.posix.memcpy

/**
 * iOS-specific implementation for launching data restore using UIDocumentPicker.
 */
@OptIn(ExperimentalForeignApi::class)
class IosRestoreLauncher(
    private val rootViewController: () -> UIViewController
) : RestoreLauncher, KoinComponent {

    private val restoreUserDataUseCase: RestoreUserDataUseCase by inject()
    private val mediaManager: MediaManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentRestoreJob: Job? = null
    private var completionCallback: ((RestoreOutcome) -> Unit)? = null

    private val documentPickerDelegate = RestoreDocumentPickerDelegate(
        onPick = { url ->
            if (url == null) {
                completionCallback?.invoke(RestoreOutcome.Cancelled)
                return@RestoreDocumentPickerDelegate
            }
            handlePickedUrl(url)
        },
        onCancel = {
            completionCallback?.invoke(RestoreOutcome.Cancelled)
        }
    )

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {
        completionCallback = callback
    }

    override fun startRestore() {
        currentRestoreJob?.cancel()
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.folder"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen
        )
        picker.allowsMultipleSelection = false
        picker.delegate = documentPickerDelegate
        rootViewController().presentViewController(
            viewControllerToPresent = picker,
            animated = true,
            completion = null
        )
    }

    override fun cancelRestore() {
        currentRestoreJob?.cancel()
        currentRestoreJob = null
        completionCallback?.invoke(RestoreOutcome.Cancelled)
    }

    private fun handlePickedUrl(url: platform.Foundation.NSURL) {
        currentRestoreJob?.cancel()
        currentRestoreJob = scope.launch {
            try {
                val path = url.path ?: run {
                    completionCallback?.invoke(RestoreOutcome.Failure("Invalid restore location"))
                    return@launch
                }

                val isDirectory = isDirectory(path)
                if (!isDirectory) {
                    completionCallback?.invoke(
                        RestoreOutcome.Failure("Restore on iOS requires a folder export")
                    )
                    return@launch
                }

                completionCallback?.invoke(RestoreOutcome.Started)

                val summary = restoreFromDirectory(path)
                completionCallback?.invoke(RestoreOutcome.Success(summary))
            } catch (e: Exception) {
                Napier.e("iOS: Restore failed", e)
                completionCallback?.invoke(RestoreOutcome.Failure("Restore failed: ${e.message}"))
            }
        }
    }

    private suspend fun restoreFromDirectory(path: String): RestoreSummary {
        val structure = ExportFileStructure()
        val bundle = RestoreBundle(
            metadataJson = readRequiredFile(path, structure.metadataFile),
            journalsJson = readRequiredFile(path, structure.journalsFile),
            notesJson = readRequiredFile(path, structure.notesFile),
            journalNotesJson = readRequiredFile(path, structure.journalNotesFile),
            draftsJson = readRequiredFile(path, structure.draftsFile),
            mediaManifestJson = readOptionalFile(path, structure.mediaManifestFile)
        )

        val mediaImporter = object : MediaImporter {
            override suspend fun importMedia(exportPath: String): String? {
                return importMedia(path, exportPath)
            }
        }

        val result = restoreUserDataUseCase.restore(bundle, RestoreOptions(), mediaImporter)
        return RestoreSummary(
            source = path,
            exportDate = result.metadata.exportDate,
            appVersion = result.metadata.appVersion,
            deviceId = result.metadata.deviceId,
            journalsImported = result.journalsImported,
            notesImported = result.notesImported,
            draftsImported = result.draftsImported,
            journalLinksImported = result.journalLinksImported,
            mediaImported = result.mediaImported,
            warnings = result.warnings
        )
    }

    private fun readRequiredFile(directoryPath: String, fileName: String): String {
        val fileUrl = resolveFileUrl(directoryPath, fileName)
            ?: throw IllegalStateException("Missing required file: $fileName")
        val data = NSData.dataWithContentsOfURL(fileUrl)
            ?: throw IllegalStateException("Missing required file: $fileName")
        return data.toByteArray().decodeToString()
    }

    private fun readOptionalFile(directoryPath: String, fileName: String): String? {
        val fileUrl = resolveFileUrl(directoryPath, fileName) ?: return null
        val data = NSData.dataWithContentsOfURL(fileUrl) ?: return null
        return data.toByteArray().decodeToString()
    }

    private suspend fun importMedia(directoryPath: String, exportPath: String): String? {
        val normalizedPath = exportPath.trimStart('/')
        val fileUrl = resolveFileUrl(directoryPath, normalizedPath) ?: return null
        val data = NSData.dataWithContentsOfURL(fileUrl) ?: return null
        val bytes = data.toByteArray()
        val payload = MediaPayload(
            fileName = normalizedPath.substringAfterLast('/'),
            mimeType = "application/octet-stream",
            sizeBytes = bytes.size.toLong(),
            data = bytes
        )
        return runCatching { mediaManager.saveMedia(payload) }
            .onFailure { Napier.e("iOS: Failed to import media", it) }
            .getOrNull()
    }

    private fun resolveFileUrl(directoryPath: String, relativePath: String): NSURL? {
        var url = NSURL.fileURLWithPath(directoryPath)
        val components = relativePath.trimStart('/').split('/')
        for (component in components) {
            if (component.isNotEmpty()) {
                url = url.URLByAppendingPathComponent(component) ?: return null
            }
        }
        return url
    }

    private fun isDirectory(path: String): Boolean = memScoped {
        val isDir = alloc<BooleanVar>()
        val exists = NSFileManager.defaultManager.fileExistsAtPath(path, isDirectory = isDir.ptr)
        exists && isDir.value
    }
}

@OptIn(ExperimentalForeignApi::class)
private class RestoreDocumentPickerDelegate(
    private val onPick: (platform.Foundation.NSURL?) -> Unit,
    private val onCancel: () -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? platform.Foundation.NSURL
        onPick(url)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onCancel()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) {
        return ByteArray(0)
    }
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), this.bytes, length.toULong())
    }
    return bytes
}
