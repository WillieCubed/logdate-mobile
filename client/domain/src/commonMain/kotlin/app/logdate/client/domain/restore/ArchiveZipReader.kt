package app.logdate.client.domain.restore

import okio.Buffer
import okio.FileHandle
import okio.Inflater
import okio.InflaterSource
import okio.Source
import okio.Timeout

/**
 * Bounded ZIP index over a caller-owned handle, without path normalization or whole-payload buffering.
 * EOF checks exact decoded bytes and CRC; unused padding inside the declared compressed range is allowed.
 */
class ArchiveZipReader(
    private val file: FileHandle,
) {
    private val entries = readArchiveZipDirectory(file)
    val entryNames: List<String> = entries.map { it.name }
    private val byName = entries.associateBy { it.name }

    init {
        require(byName.size == entries.size) { "Repeated archive entry name" }
    }

    fun containsFile(name: String): Boolean = name in byName && !name.endsWith('/')

    /** Caller closes the source. Only EOF verifies CRC and length; partial reads never imply admission. */
    fun source(name: String): Source {
        val entry = requireNotNull(byName[name]) { "Missing ZIP entry" }
        require(!entry.name.endsWith('/')) { "ZIP entry is a directory" }
        val compressed = CompressedZipSource(file.source(entry.dataOffset), entry.compressedSize)
        return try {
            val decoded = if (entry.method == 8) InflaterSource(compressed, Inflater(true)) else compressed
            VerifiedZipSource(decoded, entry.size, entry.crc)
        } catch (failure: Exception) {
            compressed.close()
            throw failure
        }
    }
}

private class CompressedZipSource(
    private val source: Source,
    private var remaining: Long,
) : Source {
    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        require(byteCount >= 0)
        if (byteCount == 0L) return 0
        if (remaining == 0L) return -1
        val read = source.read(sink, minOf(byteCount, remaining, 8_192))
        require(read in 1..remaining) { "Truncated ZIP compressed data" }
        remaining -= read
        return read
    }

    override fun timeout(): Timeout = source.timeout()

    override fun close() = source.close()
}

private class VerifiedZipSource(
    private val source: Source,
    private val expectedSize: Long,
    private val expectedCrc: Long,
) : Source {
    private var count = 0L
    private var crc = -1
    private var verified = false

    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        require(byteCount >= 0)
        if (byteCount == 0L) return 0
        if (verified) return -1
        val buffer = Buffer()
        val read = source.read(buffer, minOf(byteCount, 8_192))
        if (read == -1L) {
            require(
                count == expectedSize && (crc.inv().toLong() and 0xffffffffL) == expectedCrc,
            ) { "ZIP entry failed length or CRC verification" }
            verified = true
            return -1
        }
        require(read > 0 && read <= expectedSize - count) { "ZIP entry exceeds its recorded size" }
        val bytes = buffer.readByteArray(read)
        for (byte in bytes) crc = CRC_TABLE[(crc xor byte.toInt()) and 255] xor (crc ushr 8)
        count += read
        sink.write(bytes)
        return read
    }

    override fun timeout(): Timeout = source.timeout()

    override fun close() = source.close()

    private companion object {
        val CRC_TABLE =
            IntArray(256) { index ->
                var value = index
                repeat(8) { value = if (value and 1 != 0) (value ushr 1) xor 0xedb88320.toInt() else value ushr 1 }
                value
            }
    }
}
