package app.logdate.client.domain.export.archive

import okio.FileSystem
import okio.Path
import okio.Sink
import okio.use

/** Stages portable archive entries as a directory for native OS transfer. */
class StagingDirectoryArchiveContainer(
    internal val fileSystem: FileSystem,
    val root: Path,
) : ArchiveContainer {
    init {
        require(fileSystem.metadataOrNull(root) == null) { "Archive staging directory already exists" }
        fileSystem.createDirectories(root)
        requireDirectory(root)
    }

    override fun entry(
        path: ArchivePath,
        compress: Boolean,
        write: (Sink) -> Unit,
    ) {
        requireDirectory(root)
        var parent = root
        for (component in path.value.split('/').dropLast(1)) {
            parent /= component
            val existing = fileSystem.metadataOrNull(parent)
            if (existing == null) fileSystem.createDirectory(parent)
            requireDirectory(parent)
        }
        val target = parent / path.fileName
        require(fileSystem.metadataOrNull(target) == null) { "Archive entry already exists" }
        var created = false
        try {
            fileSystem.sink(target, mustCreate = true).use { sink ->
                created = true
                write(sink)
            }
        } catch (failure: Throwable) {
            if (created) runCatching { fileSystem.delete(target, mustExist = false) }
            throw failure
        }
    }

    fun discard() = fileSystem.deleteRecursively(root, mustExist = false)

    private fun requireDirectory(path: Path) {
        val metadata = requireNotNull(fileSystem.metadataOrNull(path)) { "Archive directory disappeared" }
        require(metadata.isDirectory && metadata.symlinkTarget == null) { "Archive directory is not a direct directory" }
    }
}
