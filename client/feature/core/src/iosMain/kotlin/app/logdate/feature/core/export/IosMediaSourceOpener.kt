package app.logdate.feature.core.export

import app.logdate.client.domain.export.archive.MediaSourceOpener
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source

/** Opens only local iOS media references; exports never fetch network URLs. */
class IosMediaSourceOpener(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : MediaSourceOpener {
    override suspend fun open(reference: String): Source? {
        val sourcePath =
            when {
                reference.startsWith("file://") -> reference.removePrefix("file://")
                reference.startsWith("/") -> reference
                else -> return null
            }.takeIf(String::isNotBlank)?.toPath() ?: return null

        val candidate = existingFile(sourcePath) ?: existingFile(sourcePath.withoutDoubledExtension()) ?: return null
        return runCatching { fileSystem.source(candidate) }.getOrNull()
    }

    private fun existingFile(path: Path): Path? = path.takeIf { fileSystem.metadataOrNull(it)?.isRegularFile == true }

    private fun Path.withoutDoubledExtension(): Path {
        val normalizedName = MediaReferenceRecovery.withoutDoubledExtension(name)
        return if (normalizedName == name) this else checkNotNull(parent) / normalizedName
    }
}
