package app.logdate.feature.core.export

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportIssue
import app.logdate.client.domain.export.ExportIssueCode
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportResult
import io.github.aakira.napier.Napier
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates the portable LogDate ZIP archive from an [ExportResult].
 *
 * The app-private entry point is intentionally separate from the user-facing export destination:
 * cloud backup can build a durable local archive without touching Downloads or a user-selected
 * document URI. Media resolution and recovery remain Android-specific because entries may refer
 * to ContentResolver URIs or legacy app-private paths.
 */
class AndroidExportArchiveWriter(
    private val context: Context,
) {
    /** Writes an archive below [Context.filesDir] and returns the durable private file. */
    fun writeToAppPrivateFile(
        exportData: ExportResult,
        fileName: String,
    ): File {
        require(File(fileName).name == fileName) { "Archive file name must not contain path separators" }
        val target = File(context.filesDir, fileName)
        FileOutputStream(target).use { output -> write(exportData, output) }
        return target
    }

    /** Writes the same archive format used by the user-facing Android export flow. */
    fun write(
        exportData: ExportResult,
        output: OutputStream,
    ) {
        ZipOutputStream(output).use { zipOut ->
            writeExportToZip(zipOut, exportData)
        }
    }

    private fun writeExportToZip(
        zipOut: ZipOutputStream,
        exportData: ExportResult,
    ) {
        writeStreamedEntry(zipOut, ExportFileStructure.METADATA_FILE) { exportData.writeMetadata(it) }
        writeStreamedEntry(zipOut, ExportFileStructure.JOURNALS_FILE) { exportData.writeJournals(it) }
        writeStreamedEntry(zipOut, ExportFileStructure.NOTES_FILE) { exportData.writeNotes(it) }
        writeStreamedEntry(zipOut, ExportFileStructure.JOURNAL_NOTES_FILE) { exportData.writeJournalNotes(it) }
        writeStreamedEntry(zipOut, ExportFileStructure.DRAFTS_FILE) { exportData.writeDrafts(it) }
        if (exportData.hasProfile) {
            writeStreamedEntry(zipOut, ExportFileStructure.PROFILE_FILE) { exportData.writeProfile(it) }
        }
        if (exportData.hasPlaces) {
            writeStreamedEntry(zipOut, ExportFileStructure.PLACES_FILE) { exportData.writePlaces(it) }
        }
        if (exportData.hasLocationHistory) {
            writeStreamedEntry(zipOut, ExportFileStructure.LOCATION_HISTORY_FILE) { exportData.writeLocationHistory(it) }
        }

        val exportedMediaFiles = mutableListOf<ExportMediaFile>()
        val archiveIssues = mutableListOf<ExportIssue>()
        exportData.mediaFiles.forEach { mediaFile ->
            val outcome = writeMediaEntry(zipOut, mediaFile)
            if (outcome.written) exportedMediaFiles += mediaFile
            outcome.issue?.let(archiveIssues::add)
        }
        if (exportData.hasMediaManifest(exportedMediaFiles)) {
            writeStreamedEntry(zipOut, ExportFileStructure.MEDIA_MANIFEST_FILE) {
                exportData.writeMediaManifest(it, exportedMediaFiles)
            }
        }
        exportData.renderIssuesText(archiveIssues)?.let { writeTextEntry(zipOut, ExportFileStructure.EXPORT_ISSUES_FILE, it) }
    }

    private fun writeStreamedEntry(
        zipOut: ZipOutputStream,
        entryName: String,
        write: (BufferedSink) -> Unit,
    ) {
        zipOut.putNextEntry(ZipEntry(entryName))
        val bufferedSink = zipOut.sink().buffer()
        write(bufferedSink)
        bufferedSink.flush()
        zipOut.closeEntry()
    }

    private fun writeTextEntry(
        zipOut: ZipOutputStream,
        entryName: String,
        content: String,
    ) {
        zipOut.putNextEntry(ZipEntry(entryName))
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
    }

    private fun writeMediaEntry(
        zipOut: ZipOutputStream,
        mediaFile: ExportMediaFile,
    ): MediaWriteResult {
        val sourceUri = mediaFile.sourceUri
        var entryOpened = false
        return try {
            val resolvedInput = resolveMediaInput(mediaFile)
                ?: return MediaWriteResult(
                    written = false,
                    issue = ExportIssue(code = ExportIssueCode.MEDIA_BYTES_MISSING, source = sourceUri),
                )
            zipOut.putNextEntry(ZipEntry(mediaFile.exportPath))
            entryOpened = true
            resolvedInput.openStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
            entryOpened = false
            Napier.d("Added media file to archive: ${mediaFile.exportPath}")
            MediaWriteResult(
                written = true,
                issue = resolvedInput.issueCode?.let { ExportIssue(code = it, source = sourceUri) },
            )
        } catch (e: Exception) {
            if (entryOpened) runCatching { zipOut.closeEntry() }
            Napier.w("Failed to add media to ZIP, skipping: $sourceUri", e)
            MediaWriteResult(
                written = false,
                issue = ExportIssue(code = ExportIssueCode.MEDIA_BYTES_MISSING, source = sourceUri, detail = e.message),
            )
        }
    }

    private fun resolveMediaInput(mediaFile: ExportMediaFile): ResolvedMediaInput? {
        val sourceUri = mediaFile.sourceUri
        if (!sourceUri.startsWith("/") && !sourceUri.startsWith("file://")) {
            val parsed = sourceUri.toUri()
            return ResolvedMediaInput(
                openStream = {
                    context.contentResolver.openInputStream(parsed)
                        ?: throw IllegalStateException("Media file cannot be opened: $sourceUri")
                },
            )
        }

        val originalFile = File(sourceUri.removePrefix("file://"))
        if (originalFile.exists()) return ResolvedMediaInput(openStream = { originalFile.inputStream() })

        recoverMediaInput(originalFile, mediaFile)?.let {
            Napier.w("Recovered stale export media reference: $sourceUri")
            return it
        }
        Napier.w("Media file not found, skipping: ${originalFile.absolutePath}")
        return null
    }

    private fun recoverMediaInput(
        originalFile: File,
        mediaFile: ExportMediaFile,
    ): ResolvedMediaInput? {
        val normalizedFile = normalizeDuplicateExtension(originalFile)
        if (normalizedFile != originalFile && normalizedFile.exists()) {
            return ResolvedMediaInput({ normalizedFile.inputStream() }, ExportIssueCode.MEDIA_RECOVERED_NORMALIZED_PATH)
        }
        extractRecordingFileName(originalFile.name)?.let { recordingFileName ->
            val audioFile = File(context.filesDir, "audio_notes/$recordingFileName")
            if (audioFile.exists()) {
                return ResolvedMediaInput({ audioFile.inputStream() }, ExportIssueCode.MEDIA_RECOVERED_APP_PRIVATE_AUDIO)
            }
        }
        recoverAppPrivateMedia(originalFile)?.let { recoveredFile ->
            return ResolvedMediaInput({ recoveredFile.inputStream() }, ExportIssueCode.MEDIA_RECOVERED_APP_PRIVATE_MEDIA)
        }
        recoverMediaStoreUri(originalFile.name, mediaFile.exportPath)?.let { mediaStoreUri ->
            return ResolvedMediaInput(
                openStream = {
                    context.contentResolver.openInputStream(mediaStoreUri)
                        ?: throw IllegalStateException("Recovered media URI cannot be opened: $mediaStoreUri")
                },
                issueCode = ExportIssueCode.MEDIA_RECOVERED_MEDIA_STORE,
            )
        }
        return null
    }

    private fun recoverAppPrivateMedia(originalFile: File): File? {
        val candidates = File(context.filesDir, "user_media").listFiles().orEmpty()
        if (candidates.isEmpty()) return null
        val fileName = originalFile.name
        val baseName = fileName.substringBeforeLast('.', fileName)
        val trailingToken = fileName.substringAfterLast('_', "")
        val trailingStem = trailingToken.substringBeforeLast('.', trailingToken)
        val matchKeys = listOf(fileName, baseName, trailingToken, trailingStem).filter { it.isNotBlank() }.distinct()
        return candidates.firstOrNull { candidate ->
            val candidateName = candidate.name
            matchKeys.any { key -> candidateName == key || candidateName.startsWith("$key.") || candidateName.contains("_$key") }
        }
    }

    private fun normalizeDuplicateExtension(file: File): File {
        val normalizedName = file.name.replace(Regex("(\\.[A-Za-z0-9]+)\\1$")) { it.groupValues[1] }
        return if (normalizedName == file.name) file else File(file.parentFile, normalizedName)
    }

    private fun extractRecordingFileName(fileName: String): String? {
        val match = Regex("(recording_[A-Za-z0-9-]+(?:\\.[A-Za-z0-9]+)?)").find(fileName) ?: return null
        val normalized = match.value.replace(Regex("(\\.[A-Za-z0-9]+)\\1$")) { it.groupValues[1] }
        return if ('.' in normalized) normalized else "$normalized.m4a"
    }

    private fun recoverMediaStoreUri(
        fileName: String,
        exportPath: String,
    ): Uri? {
        val legacyId = Regex("(\\d{6,})$").find(fileName)?.groupValues?.get(1) ?: return null
        val collection =
            when {
                exportPath.endsWith(".jpg") || exportPath.endsWith(".jpeg") || exportPath.endsWith(".png") ->
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                exportPath.endsWith(".mp4") || exportPath.endsWith(".mov") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                exportPath.endsWith(".m4a") || exportPath.endsWith(".aac") || exportPath.endsWith(".wav") ->
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> return null
            }
        return Uri.withAppendedPath(collection, legacyId)
    }

    private data class ResolvedMediaInput(
        val openStream: () -> InputStream,
        val issueCode: ExportIssueCode? = null,
    )

    private data class MediaWriteResult(
        val written: Boolean,
        val issue: ExportIssue? = null,
    )
}
