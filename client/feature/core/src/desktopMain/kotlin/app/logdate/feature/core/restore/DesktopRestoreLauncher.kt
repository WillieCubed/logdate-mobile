package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.ArchiveMediaIntegrity
import app.logdate.client.domain.restore.RestoreArchiveReader
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.domain.restore.copyArchiveMedia
import app.logdate.client.domain.restore.mediaIntegrity
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okio.sink
import okio.source
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URLConnection
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Desktop-specific implementation for restoring LogDate ZIP exports.
 */
class DesktopRestoreLauncher :
    RestoreLauncher,
    KoinComponent {
    private val restoreUserDataUseCase: RestoreUserDataUseCase by inject()
    private val mediaManager: MediaManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentRestoreJob: Job? = null
    private var completionCallback: ((RestoreOutcome) -> Unit)? = null
    private var fileSelectedCallback: ((ArchiveFileInfo?) -> Unit)? = null
    private var selectedFile: File? = null

    private val _restoreProgress = MutableStateFlow<RestoreProgressInfo>(RestoreProgressInfo.Idle)
    override val restoreProgress: StateFlow<RestoreProgressInfo> = _restoreProgress.asStateFlow()

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
        currentRestoreJob =
            scope.launch {
                val fileDialog = createOpenDialog()
                val file =
                    fileDialog.directory?.let { dir ->
                        val fileName = fileDialog.file ?: return@let null
                        File(dir, fileName)
                    }

                if (file == null) {
                    Napier.i("Desktop: Restore cancelled by user")
                    fileSelectedCallback?.invoke(null)
                    return@launch
                }

                selectedFile = file

                val metadataJson = extractMetadata(file)
                if (metadataJson == null) {
                    fileSelectedCallback?.invoke(null)
                    completionCallback?.invoke(
                        RestoreOutcome.Failure(RestoreError.INVALID_ARCHIVE),
                    )
                    return@launch
                }

                fileSelectedCallback?.invoke(
                    ArchiveFileInfo(
                        displayName = file.name,
                        uri = file.absolutePath,
                        metadataJson = metadataJson,
                    ),
                )
            }
    }

    override fun startRestore(options: ImportOptions) {
        val file =
            selectedFile ?: run {
                completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.MISSING_SOURCE))
                return
            }

        currentRestoreJob?.cancel()
        currentRestoreJob =
            scope.launch {
                try {
                    completionCallback?.invoke(RestoreOutcome.Started)
                    _restoreProgress.value = RestoreProgressInfo.Idle

                    val summary = restoreFromZip(file, options)
                    _restoreProgress.value = RestoreProgressInfo.Idle
                    completionCallback?.invoke(RestoreOutcome.Success(summary))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Napier.e("Desktop: Restore failed")
                    _restoreProgress.value = RestoreProgressInfo.Idle
                    completionCallback?.invoke(RestoreOutcome.Failure(RestoreError.RESTORE_FAILED))
                }
            }
    }

    override fun cancelRestore() {
        currentRestoreJob?.cancel()
        currentRestoreJob = null
        selectedFile = null
        _restoreProgress.value = RestoreProgressInfo.Idle
        completionCallback?.invoke(RestoreOutcome.Cancelled)
        Napier.i("Desktop: Restore cancelled")
    }

    private fun extractMetadata(file: File): String? {
        return try {
            ZipFile(file).use { zip ->
                val entryNames = zip.entries().toList().map { it.name }
                RestoreArchiveReader.previewJson(entryNames) { entryName ->
                    val entry = zip.getEntry(entryName) ?: return@previewJson null
                    zip.getInputStream(entry).use { input -> input.bufferedReader(Charsets.UTF_8).readText() }
                }
            }
        } catch (e: Exception) {
            Napier.e("Desktop: Failed to extract metadata")
            null
        }
    }

    private suspend fun restoreFromZip(
        file: File,
        options: ImportOptions,
    ): RestoreSummary {
        ZipFile(file).use { zipFile ->
            updateProgress(RestoreStage.PREPARING.toProgressInfo())
            updateProgress(RestoreStage.OPENING_ARCHIVE.toProgressInfo())

            updateProgress(RestoreStage.READING_CONTENTS.toProgressInfo())
            val archive =
                RestoreArchiveReader.readBytes(zipFile.entries().toList().map { it.name }) { entryName ->
                    readOptionalEntry(zipFile, entryName)
                }
            val root = archive.root

            val mediaImporter =
                createArchiveMediaImporter(
                    mediaManager = mediaManager,
                    references = getKoin().get(),
                    contains = { exportPath -> zipFile.getEntry(root + exportPath.trimStart('/'))?.isDirectory == false },
                    importFile = { exportPath -> importMedia(zipFile, root, exportPath, archive.mediaIntegrity) },
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
            return result.toSummary(source = file.name)
        }
    }

    private fun readOptionalEntry(
        zipFile: ZipFile,
        entryName: String,
    ): ByteArray? {
        val entry = zipFile.getEntry(entryName) ?: return null
        return zipFile.getInputStream(entry).use { input ->
            input.readBytes()
        }
    }

    private suspend fun importMedia(
        zipFile: ZipFile,
        root: String,
        exportPath: String,
        integrity: Map<String, ArchiveMediaIntegrity>?,
    ): String? {
        val normalizedPath = exportPath.trimStart('/')
        val entry = zipFile.getEntry(root + normalizedPath)
        if (entry == null) {
            Napier.w("Restore attachment missing")
            return null
        }
        if (entry.isDirectory) {
            Napier.w("Restore attachment is a directory")
            return null
        }
        val temporary = Files.createTempFile("logdate_restore_media_", ".partial").toFile()
        return try {
            zipFile.getInputStream(entry).use { input ->
                temporary.outputStream().use { output ->
                    copyArchiveMedia(input.source(), output.sink(), normalizedPath, integrity)
                }
            }
            mediaManager.saveMediaFromFile(temporary.absolutePath, normalizedPath.substringAfterLast('/'), resolveMimeType(normalizedPath))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Napier.e("Restore attachment import failed")
            null
        } finally {
            temporary.delete()
        }
    }

    private fun resolveMimeType(fileName: String): String = URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"

    private fun createOpenDialog(): FileDialog =
        FileDialog(null as Frame?, "Select LogDate Backup", FileDialog.LOAD).apply {
            isMultipleMode = false
            isVisible = true
        }
}
