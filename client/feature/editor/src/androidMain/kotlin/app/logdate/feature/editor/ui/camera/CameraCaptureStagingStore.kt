package app.logdate.feature.editor.ui.camera

import java.io.File
import kotlin.uuid.Uuid

/** Durable private staging for camera output that must survive activity teardown and process restart. */
internal class CameraCaptureStagingStore(
    private val directory: File,
) {
    init {
        require(directory.isDirectory || directory.mkdirs()) {
            "Unable to create camera capture staging directory: ${directory.absolutePath}"
        }
    }

    fun create(fileName: String): File {
        require(fileName.fileExtensionMimeType() != null) { "Unsupported camera capture file name: $fileName" }
        return File(directory, "$fileName.${Uuid.random()}.$TEMP_SUFFIX")
    }

    fun recoverableFiles(): List<PendingCapture> =
        directory
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.name.endsWith(".$TEMP_SUFFIX") }
            .mapNotNull(::parse)
            .sortedBy { it.file.lastModified() }
            .toList()

    private fun parse(file: File): PendingCapture? {
        val match = CAPTURE_PATTERN.matchEntire(file.name) ?: return null
        val fileName = match.groupValues[1]
        val mimeType = fileName.fileExtensionMimeType() ?: return null
        return PendingCapture(file = file, fileName = fileName, mimeType = mimeType)
    }

    data class PendingCapture(
        val file: File,
        val fileName: String,
        val mimeType: String,
    )

    private companion object {
        const val TEMP_SUFFIX = "tmp"
        val CAPTURE_PATTERN = Regex("^(.+\\.(?:jpg|jpeg|heic|heif|png|webp|mp4|mov|mkv))\\.[0-9a-fA-F-]{36}\\.$TEMP_SUFFIX$")
    }
}

private fun String.fileExtensionMimeType(): String? =
    when (substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        else -> null
    }
