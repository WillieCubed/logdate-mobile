package app.logdate.feature.core.restore

import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
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
import java.net.URLConnection
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
                } catch (e: Exception) {
                    Napier.e("Desktop: Restore failed", e)
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
                val entry = zip.getEntry(ExportFileStructure.METADATA_FILE) ?: return null
                zip.getInputStream(entry).use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }
            }
        } catch (e: Exception) {
            Napier.e("Desktop: Failed to extract metadata", e)
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
            val bundle =
                RestoreBundle(
                    metadataJson = readRequiredEntry(zipFile, ExportFileStructure.METADATA_FILE),
                    journalsJson = readRequiredEntry(zipFile, ExportFileStructure.JOURNALS_FILE),
                    notesJson = readRequiredEntry(zipFile, ExportFileStructure.NOTES_FILE),
                    journalNotesJson = readRequiredEntry(zipFile, ExportFileStructure.JOURNAL_NOTES_FILE),
                    draftsJson = readRequiredEntry(zipFile, ExportFileStructure.DRAFTS_FILE),
                    profileJson = readOptionalEntry(zipFile, ExportFileStructure.PROFILE_FILE),
                    placesJson = readOptionalEntry(zipFile, ExportFileStructure.PLACES_FILE),
                    locationHistoryJson = readOptionalEntry(zipFile, ExportFileStructure.LOCATION_HISTORY_FILE),
                    mediaManifestJson = readOptionalEntry(zipFile, ExportFileStructure.MEDIA_MANIFEST_FILE),
                )

            val mediaImporter =
                if (options.includeMedia) {
                    object : MediaImporter {
                        override suspend fun importMedia(exportPath: String): String? =
                            this@DesktopRestoreLauncher.importMedia(zipFile, exportPath)
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
            return result.toSummary(source = file.name)
        }
    }

    private fun readRequiredEntry(
        zipFile: ZipFile,
        entryName: String,
    ): String {
        val entry =
            zipFile.getEntry(entryName)
                ?: throw IllegalStateException("Missing required file: $entryName")
        return zipFile.getInputStream(entry).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun readOptionalEntry(
        zipFile: ZipFile,
        entryName: String,
    ): String? {
        val entry = zipFile.getEntry(entryName) ?: return null
        return zipFile.getInputStream(entry).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    private suspend fun importMedia(
        zipFile: ZipFile,
        exportPath: String,
    ): String? {
        val normalizedPath = exportPath.trimStart('/')
        val entry = zipFile.getEntry(normalizedPath)
        if (entry == null) {
            Napier.w("Desktop: Media file not found in archive at path: $exportPath")
            return null
        }
        if (entry.isDirectory) {
            Napier.w("Desktop: Expected file but found directory in archive at path: $exportPath")
            return null
        }
        return runCatching {
            val data = zipFile.getInputStream(entry).use { input -> input.readBytes() }
            val fileName = normalizedPath.substringAfterLast('/')
            val payload =
                MediaPayload(
                    fileName = fileName,
                    mimeType = resolveMimeType(fileName),
                    sizeBytes = data.size.toLong(),
                    data = data,
                )
            val savedPath = mediaManager.saveMedia(payload)
            if (savedPath != null) {
                Napier.d("Desktop: Successfully imported media from archive: $exportPath")
            } else {
                Napier.w("Desktop: Failed to save media during restore: $fileName")
            }
            savedPath
        }.onFailure { Napier.e("Desktop: Exception importing media from archive at path: $exportPath", it) }
            .getOrNull()
    }

    private fun resolveMimeType(fileName: String): String = URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"

    private fun createOpenDialog(): FileDialog =
        FileDialog(null as Frame?, "Select LogDate Backup", FileDialog.LOAD).apply {
            isMultipleMode = false
            isVisible = true
        }
}
