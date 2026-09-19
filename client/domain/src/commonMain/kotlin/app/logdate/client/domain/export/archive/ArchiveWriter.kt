package app.logdate.client.domain.export.archive

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer
import okio.BufferedSink
import okio.Source

/**
 * Writes the archive's files into a container, hashing each one as it goes.
 *
 * Every file passes through [hashedWrite], so [ledger] ends up holding the size and SHA-256 of
 * everything written and no file is ever loaded into memory whole.
 */
internal class ArchiveWriter(
    private val container: ArchiveContainer,
    val ledger: HashLedger = HashLedger(),
) {
    fun text(
        path: ArchivePath,
        content: String,
    ) = entry(path, compress = true) { it.writeUtf8(content) }

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> document(
        path: ArchivePath,
        serializer: SerializationStrategy<T>,
        value: T,
    ) = entry(path, compress = true) { sink ->
        ArchiveJson.document.encodeToBufferedSink(serializer, value, sink)
        sink.writeUtf8("\n")
    }

    /** One JSON record per line, written as the sequence is read so a long history is never loaded whole. */
    fun <T> lines(
        path: ArchivePath,
        serializer: SerializationStrategy<T>,
        records: Sequence<T>,
    ) = entry(path, compress = true) { sink ->
        for (record in records) {
            sink.writeUtf8(ArchiveJson.line.encodeToString(serializer, record))
            sink.writeUtf8("\n")
        }
    }

    /** Copies [source] into the archive, stopping promptly if [job] is cancelled. */
    fun media(
        path: ArchivePath,
        source: Source,
        job: Job?,
    ) = entry(path, compress = false) { sink ->
        val chunk = Buffer()
        while (true) {
            job?.ensureActive()
            val read = source.read(chunk, CHUNK_BYTES)
            if (read == -1L) break
            sink.write(chunk, read)
        }
    }

    private fun entry(
        path: ArchivePath,
        compress: Boolean,
        write: (BufferedSink) -> Unit,
    ) = container.entry(path, compress) { sink -> hashedWrite(sink, path, ledger, write) }

    private companion object {
        const val CHUNK_BYTES = 64L * 1024
    }
}
