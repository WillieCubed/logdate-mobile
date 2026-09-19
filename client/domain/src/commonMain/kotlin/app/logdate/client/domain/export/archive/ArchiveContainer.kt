package app.logdate.client.domain.export.archive

import okio.Sink

/**
 * Where an archive's files go. Each platform supplies one that writes a zip, so the layout and
 * contents are decided here and only the container format is platform code.
 */
interface ArchiveContainer {
    /**
     * Starts the file [path], hands [write] a sink for its bytes, and ends the file when [write]
     * returns. [compress] is false for media, which is already compressed.
     */
    fun entry(
        path: ArchivePath,
        compress: Boolean = true,
        write: (Sink) -> Unit,
    )
}
