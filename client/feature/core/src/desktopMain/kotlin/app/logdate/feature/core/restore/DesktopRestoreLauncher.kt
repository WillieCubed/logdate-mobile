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
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URLConnection
import java.util.zip.ZipFile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Desktop-specific implementation for launching data restore using AWT FileDialog.
 */
class DesktopRestoreLauncher : RestoreLauncher, KoinComponent {

    private val restoreUserDataUseCase: RestoreUserDataUseCase by inject()
    private val mediaManager: MediaManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentRestoreJob: Job? = null
    private var completionCallback: ((RestoreOutcome) -> Unit)? = null

    override fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit) {
        completionCallback = callback
    }

    override fun startRestore() {
        currentRestoreJob?.cancel()
        currentRestoreJob = scope.launch {
            try {
                val fileDialog = createOpenDialog()
                val selectedFile = fileDialog.directory?.let { dir ->
                    val fileName = fileDialog.file ?: return@let null
                    File(dir, fileName)
                }

                if (selectedFile == null) {
                    Napier.i("Desktop: Restore cancelled by user")
                    completionCallback?.invoke(RestoreOutcome.Cancelled)
                    return@launch
                }

                completionCallback?.invoke(RestoreOutcome.Started)

                val summary = if (selectedFile.isDirectory) {
                    restoreFromDirectory(selectedFile)
                } else {
                    restoreFromZip(selectedFile)
                }

                completionCallback?.invoke(RestoreOutcome.Success(summary))
            } catch (e: Exception) {
                Napier.e("Desktop: Restore failed", e)
                completionCallback?.invoke(RestoreOutcome.Failure("Restore failed: ${e.message}"))
            }
        }
    }

    override fun cancelRestore() {
        currentRestoreJob?.cancel()
        currentRestoreJob = null
        completionCallback?.invoke(RestoreOutcome.Cancelled)
        Napier.i("Desktop: Restore cancelled")
    }

    private suspend fun restoreFromZip(file: File): RestoreSummary {
        ZipFile(file).use { zipFile ->
            val structure = ExportFileStructure()
            val bundle = RestoreBundle(
                metadataJson = readRequiredEntry(zipFile, structure.metadataFile),
                journalsJson = readRequiredEntry(zipFile, structure.journalsFile),
                notesJson = readRequiredEntry(zipFile, structure.notesFile),
                journalNotesJson = readRequiredEntry(zipFile, structure.journalNotesFile),
                draftsJson = readRequiredEntry(zipFile, structure.draftsFile),
                mediaManifestJson = readOptionalEntry(zipFile, structure.mediaManifestFile)
            )

            val mediaImporter = object : MediaImporter {
                override suspend fun importMedia(exportPath: String): String? {
                    return importMedia(zipFile, exportPath)
                }
            }

            val result = restoreUserDataUseCase.restore(bundle, RestoreOptions(), mediaImporter)
            return RestoreSummary(
                source = file.absolutePath,
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
    }

    private suspend fun restoreFromDirectory(directory: File): RestoreSummary {
        val structure = ExportFileStructure()
        val bundle = RestoreBundle(
            metadataJson = readRequiredFile(directory, structure.metadataFile),
            journalsJson = readRequiredFile(directory, structure.journalsFile),
            notesJson = readRequiredFile(directory, structure.notesFile),
            journalNotesJson = readRequiredFile(directory, structure.journalNotesFile),
            draftsJson = readRequiredFile(directory, structure.draftsFile),
            mediaManifestJson = readOptionalFile(directory, structure.mediaManifestFile)
        )

        val mediaImporter = object : MediaImporter {
            override suspend fun importMedia(exportPath: String): String? {
                return importMedia(directory, exportPath)
            }
        }

        val result = restoreUserDataUseCase.restore(bundle, RestoreOptions(), mediaImporter)
        return RestoreSummary(
            source = directory.absolutePath,
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

    private fun readRequiredEntry(zipFile: ZipFile, entryName: String): String {
        val entry = zipFile.getEntry(entryName)
            ?: throw IllegalStateException("Missing required file: $entryName")
        return zipFile.getInputStream(entry).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun readOptionalEntry(zipFile: ZipFile, entryName: String): String? {
        val entry = zipFile.getEntry(entryName) ?: return null
        return zipFile.getInputStream(entry).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun readRequiredFile(directory: File, fileName: String): String {
        val file = File(directory, fileName)
        if (!file.exists()) {
            throw IllegalStateException("Missing required file: $fileName")
        }
        return file.readText()
    }

    private fun readOptionalFile(directory: File, fileName: String): String? {
        val file = File(directory, fileName)
        if (!file.exists()) {
            return null
        }
        return file.readText()
    }

    private suspend fun importMedia(zipFile: ZipFile, exportPath: String): String? {
        val normalizedPath = exportPath.trimStart('/')
        val entry = zipFile.getEntry(normalizedPath) ?: return null
        if (entry.isDirectory) {
            return null
        }
        val data = zipFile.getInputStream(entry).use { input -> input.readBytes() }
        val fileName = normalizedPath.substringAfterLast('/')
        val payload = MediaPayload(
            fileName = fileName,
            mimeType = resolveMimeType(fileName),
            sizeBytes = data.size.toLong(),
            data = data
        )
        return runCatching { mediaManager.saveMedia(payload) }
            .onFailure { Napier.e("Desktop: Failed to import media", it) }
            .getOrNull()
    }

    private suspend fun importMedia(directory: File, exportPath: String): String? {
        val normalizedPath = exportPath.trimStart('/')
        val file = File(directory, normalizedPath)
        if (!file.exists() || file.isDirectory) {
            return null
        }
        val data = file.readBytes()
        val payload = MediaPayload(
            fileName = file.name,
            mimeType = resolveMimeType(file.name),
            sizeBytes = data.size.toLong(),
            data = data
        )
        return runCatching { mediaManager.saveMedia(payload) }
            .onFailure { Napier.e("Desktop: Failed to import media", it) }
            .getOrNull()
    }

    private fun resolveMimeType(fileName: String): String {
        return URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
    }

    private fun createOpenDialog(): FileDialog {
        return FileDialog(null as Frame?, "Select LogDate Backup", FileDialog.LOAD).apply {
            isMultipleMode = false
            isVisible = true
        }
    }
}
