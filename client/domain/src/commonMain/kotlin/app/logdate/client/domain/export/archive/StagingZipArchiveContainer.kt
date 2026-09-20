package app.logdate.client.domain.export.archive

import app.logdate.client.domain.export.ZipArchiveEntry
import app.logdate.client.domain.export.ZipArchiveWriter
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.use

/**
 * Stages archive entries as files before assembling the portable ZIP.
 *
 * This keeps generated JSON and large media off the heap and gives platforms without a streaming
 * ZIP API, notably iOS, the same archive writer as the other targets.
 */
class StagingZipArchiveContainer(
    private val fileSystem: FileSystem,
    private val stagingDirectory: Path,
) : ArchiveContainer {
    private val entries = mutableListOf<ZipArchiveEntry.File>()

    override fun entry(
        path: ArchivePath,
        compress: Boolean,
        write: (Sink) -> Unit,
    ) {
        val target = stagingDirectory / path.value
        try {
            target.parent?.let(fileSystem::createDirectories)
            fileSystem.sink(target).use(write)
            entries += ZipArchiveEntry.File(path.value, target)
        } catch (failure: Throwable) {
            cleanup()
            throw failure
        }
    }

    fun finish(outputPath: Path) {
        try {
            outputPath.parent?.let(fileSystem::createDirectories)
            ZipArchiveWriter(fileSystem).write(outputPath, entries)
        } catch (failure: Throwable) {
            fileSystem.delete(outputPath, mustExist = false)
            throw failure
        } finally {
            cleanup()
        }
    }

    /** Removes entries staged by an export that did not reach [finish]. */
    fun discard() = cleanup()

    private fun cleanup() {
        fileSystem.deleteRecursively(stagingDirectory, mustExist = false)
        entries.clear()
    }
}
