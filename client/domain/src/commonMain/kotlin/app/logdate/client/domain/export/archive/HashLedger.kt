package app.logdate.client.domain.export.archive

import okio.Buffer
import okio.BufferedSink
import okio.HashingSink
import okio.Sink
import okio.Timeout
import okio.buffer

/**
 * The size and SHA-256 of every file written into an archive, in the order they were written.
 *
 * `SHA256SUMS` and the media inventory are both rendered from this one list, so they cannot
 * disagree about a file.
 */
class HashLedger {
    class Entry(
        val path: ArchivePath,
        val sha256: String,
        val bytes: Long,
    )

    private val recorded = mutableListOf<Entry>()

    val entries: List<Entry> get() = recorded.toList()

    fun record(
        path: ArchivePath,
        sha256: String,
        bytes: Long,
    ) {
        require(recorded.none { it.path == path }) { "A file was written to the archive twice" }
        recorded += Entry(path, sha256, bytes)
    }

    operator fun get(path: ArchivePath): Entry? = recorded.firstOrNull { it.path == path }
}

/**
 * Runs [write] against [sink] and records the SHA-256 and size of the bytes it wrote under [path].
 *
 * The bytes are hashed as they pass through, so a large file is never held in memory. [sink] is
 * flushed but not closed, so it can be an entry inside a larger archive.
 */
fun hashedWrite(
    sink: Sink,
    path: ArchivePath,
    ledger: HashLedger,
    write: (BufferedSink) -> Unit,
) {
    val counting = CountingSink(sink)
    val hashing = HashingSink.sha256(counting)
    val buffered = hashing.buffer()
    write(buffered)
    buffered.flush()
    ledger.record(path, hashing.hash.hex(), counting.bytes)
}

private class CountingSink(
    private val delegate: Sink,
) : Sink {
    var bytes = 0L
        private set

    override fun write(
        source: Buffer,
        byteCount: Long,
    ) {
        delegate.write(source, byteCount)
        bytes += byteCount
    }

    override fun flush() = delegate.flush()

    override fun timeout(): Timeout = delegate.timeout()

    override fun close() = delegate.close()
}
