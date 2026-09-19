package app.logdate.client.domain.export.archive.support

import app.logdate.client.domain.export.archive.ArchiveContainer
import app.logdate.client.domain.export.archive.ArchivePath
import okio.Buffer
import okio.Sink

/** Collects an archive's files in the order they were written, so tests can inspect them. */
class InMemoryArchiveContainer : ArchiveContainer {
    private val written = linkedMapOf<String, ByteArray>()

    /** File paths in the order they were written. */
    val paths: List<String> get() = written.keys.toList()

    override fun entry(
        path: ArchivePath,
        compress: Boolean,
        write: (Sink) -> Unit,
    ) {
        check(path.value !in written) { "${path.value} was written twice" }
        val buffer = Buffer()
        write(buffer)
        written[path.value] = buffer.readByteArray()
    }

    fun bytes(path: String): ByteArray = written.getValue(path)

    fun text(path: String): String = bytes(path).decodeToString()

    fun has(path: String): Boolean = path in written

    /** Every file whose text a person or a program would read, for scanning. */
    fun textFiles(): Map<String, String> =
        written
            .filterKeys { it.endsWith(".json") || it.endsWith(".jsonl") || it.endsWith(".txt") || it == "SHA256SUMS" }
            .mapValues { it.value.decodeToString() }
}
