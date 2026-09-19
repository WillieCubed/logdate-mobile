package app.logdate.feature.core.export

import app.logdate.client.domain.export.archive.ArchiveContainer
import app.logdate.client.domain.export.archive.ArchivePath
import okio.Sink
import okio.sink
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an archive's files into a zip stream.
 *
 * Names are stored as UTF-8. Media is written without compression since it is already compressed,
 * and everything else is deflated. The caller finishes and closes [zip].
 */
class ZipStreamArchiveContainer(
    private val zip: ZipOutputStream,
) : ArchiveContainer {
    override fun entry(
        path: ArchivePath,
        compress: Boolean,
        write: (Sink) -> Unit,
    ) {
        zip.setLevel(if (compress) Deflater.DEFAULT_COMPRESSION else Deflater.NO_COMPRESSION)
        zip.putNextEntry(ZipEntry(path.value))
        write(zip.sink())
        zip.closeEntry()
    }
}
