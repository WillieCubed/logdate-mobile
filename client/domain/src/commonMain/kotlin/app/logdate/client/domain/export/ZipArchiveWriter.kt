package app.logdate.client.domain.export

import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer

/**
 * Minimal ZIP archive writer for portable LogDate exports.
 *
 * Archives are written using the stored method so they can be created
 * consistently across Android, desktop, and iOS without a platform ZIP API.
 */
class ZipArchiveWriter(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    fun write(
        outputPath: Path,
        entries: List<ZipArchiveEntry>,
    ) {
        val sink = fileSystem.sink(outputPath).buffer()
        try {
            val centralDirectoryEntries = mutableListOf<CentralDirectoryEntry>()
            var offset = 0L

            entries.forEach { entry ->
                val normalizedPath = entry.path.trimStart('/')
                require(normalizedPath.isNotBlank()) { "ZIP entry path cannot be blank" }

                val nameBytes = normalizedPath.encodeToByteArray()
                require(nameBytes.size <= MAX_SHORT_VALUE) {
                    "ZIP entry path is too long: $normalizedPath"
                }

                val localHeaderOffset = offset
                writeLocalHeader(sink, nameBytes)
                offset += LOCAL_FILE_HEADER_SIZE + nameBytes.size

                val checksum = Crc32()
                val sizeBytes =
                    when (entry) {
                        is ZipArchiveEntry.Bytes -> {
                            checksum.update(entry.bytes)
                            sink.write(entry.bytes)
                            entry.bytes.size.toLong()
                        }

                        is ZipArchiveEntry.File -> {
                            writeFileContents(sink, entry.sourcePath, checksum)
                        }
                    }
                requireZip32Limit(sizeBytes, "Entry size exceeds ZIP32 limit: $normalizedPath")
                sink.writeDataDescriptor(checksum.value, sizeBytes)
                offset += sizeBytes + DATA_DESCRIPTOR_SIZE

                centralDirectoryEntries +=
                    CentralDirectoryEntry(
                        nameBytes = nameBytes,
                        crc32 = checksum.value,
                        sizeBytes = sizeBytes,
                        localHeaderOffset = localHeaderOffset,
                    )
            }

            val centralDirectoryOffset = offset
            centralDirectoryEntries.forEach { entry ->
                requireZip32Limit(entry.localHeaderOffset, "Archive offset exceeds ZIP32 limit: ${entry.name}")
                writeCentralDirectoryHeader(sink, entry)
                offset += CENTRAL_DIRECTORY_HEADER_SIZE + entry.nameBytes.size
            }

            val centralDirectorySize = offset - centralDirectoryOffset
            require(centralDirectoryEntries.size <= MAX_SHORT_VALUE) {
                "ZIP entry count exceeds ZIP32 limit: ${centralDirectoryEntries.size}"
            }
            requireZip32Limit(centralDirectoryOffset, "Central directory offset exceeds ZIP32 limit")
            requireZip32Limit(centralDirectorySize, "Central directory size exceeds ZIP32 limit")

            sink.writeEndOfCentralDirectory(
                entryCount = centralDirectoryEntries.size,
                centralDirectorySize = centralDirectorySize,
                centralDirectoryOffset = centralDirectoryOffset,
            )
        } finally {
            sink.close()
        }
    }

    private fun writeFileContents(
        sink: BufferedSink,
        sourcePath: Path,
        checksum: Crc32,
    ): Long {
        val buffer = Buffer()
        var totalBytes = 0L

        try {
            val source = fileSystem.source(sourcePath).buffer()
            try {
                while (true) {
                    val read = source.read(buffer, CHUNK_SIZE)
                    if (read == -1L) {
                        break
                    }
                    val chunk = buffer.readByteArray(read)
                    checksum.update(chunk)
                    sink.write(chunk)
                    totalBytes += read
                }
            } finally {
                source.close()
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to read ZIP entry source: $sourcePath", e)
        }

        return totalBytes
    }

    private fun writeLocalHeader(
        sink: BufferedSink,
        nameBytes: ByteArray,
    ) {
        sink.writeIntLe(LOCAL_FILE_HEADER_SIGNATURE)
        sink.writeShortLe(VERSION_NEEDED)
        sink.writeShortLe(GENERAL_PURPOSE_FLAGS)
        sink.writeShortLe(COMPRESSION_METHOD_STORED)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeIntLe(0)
        sink.writeIntLe(0)
        sink.writeIntLe(0)
        sink.writeShortLe(nameBytes.size)
        sink.writeShortLe(0)
        sink.write(nameBytes)
    }

    private fun writeCentralDirectoryHeader(
        sink: BufferedSink,
        entry: CentralDirectoryEntry,
    ) {
        sink.writeIntLe(CENTRAL_DIRECTORY_SIGNATURE)
        sink.writeShortLe(VERSION_MADE_BY)
        sink.writeShortLe(VERSION_NEEDED)
        sink.writeShortLe(GENERAL_PURPOSE_FLAGS)
        sink.writeShortLe(COMPRESSION_METHOD_STORED)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeIntLe(entry.crc32)
        sink.writeIntLe(entry.sizeBytes)
        sink.writeIntLe(entry.sizeBytes)
        sink.writeShortLe(entry.nameBytes.size)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeIntLe(0)
        sink.writeIntLe(entry.localHeaderOffset)
        sink.write(entry.nameBytes)
    }

    private fun BufferedSink.writeDataDescriptor(
        crc32: Long,
        sizeBytes: Long,
    ) {
        writeIntLe(DATA_DESCRIPTOR_SIGNATURE)
        writeIntLe(crc32)
        writeIntLe(sizeBytes)
        writeIntLe(sizeBytes)
    }

    private fun BufferedSink.writeEndOfCentralDirectory(
        entryCount: Int,
        centralDirectorySize: Long,
        centralDirectoryOffset: Long,
    ) {
        writeIntLe(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        writeShortLe(0)
        writeShortLe(0)
        writeShortLe(entryCount)
        writeShortLe(entryCount)
        writeIntLe(centralDirectorySize)
        writeIntLe(centralDirectoryOffset)
        writeShortLe(0)
    }

    private fun BufferedSink.writeShortLe(value: Int) {
        writeByte(value and 0xff)
        writeByte(value ushr 8 and 0xff)
    }

    private fun BufferedSink.writeIntLe(value: Long) {
        writeByte((value and 0xff).toInt())
        writeByte((value ushr 8 and 0xff).toInt())
        writeByte((value ushr 16 and 0xff).toInt())
        writeByte((value ushr 24 and 0xff).toInt())
    }

    private fun requireZip32Limit(
        value: Long,
        message: String,
    ) {
        require(value in 0..MAX_INT_VALUE.toLong()) { message }
    }

    private data class CentralDirectoryEntry(
        val nameBytes: ByteArray,
        val crc32: Long,
        val sizeBytes: Long,
        val localHeaderOffset: Long,
    ) {
        val name: String
            get() = nameBytes.decodeToString()
    }

    companion object {
        private const val CHUNK_SIZE = 8_192L
        private const val LOCAL_FILE_HEADER_SIZE = 30L
        private const val CENTRAL_DIRECTORY_HEADER_SIZE = 46L
        private const val DATA_DESCRIPTOR_SIZE = 16L
        private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
        private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
        private const val DATA_DESCRIPTOR_SIGNATURE = 0x08074b50L
        private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
        private const val VERSION_MADE_BY = 20
        private const val VERSION_NEEDED = 20
        private const val GENERAL_PURPOSE_FLAGS = 0x08
        private const val COMPRESSION_METHOD_STORED = 0
        private const val MAX_SHORT_VALUE = 0xffff
        private const val MAX_INT_VALUE = Int.MAX_VALUE
    }
}

sealed interface ZipArchiveEntry {
    val path: String

    data class Bytes(
        override val path: String,
        val bytes: ByteArray,
    ) : ZipArchiveEntry

    data class File(
        override val path: String,
        val sourcePath: Path,
    ) : ZipArchiveEntry
}

private class Crc32 {
    private var state: Int = -1

    val value: Long
        get() = (state.inv().toLong() and 0xffffffffL)

    fun update(bytes: ByteArray) {
        bytes.forEach { byte ->
            val lookupIndex = (state xor (byte.toInt() and 0xff)) and 0xff
            state = CRC_TABLE[lookupIndex] xor (state ushr 8)
        }
    }

    companion object {
        private val CRC_TABLE: IntArray =
            IntArray(256) { index ->
                var value = index
                repeat(8) {
                    value =
                        if ((value and 1) != 0) {
                            0xedb88320.toInt() xor (value ushr 1)
                        } else {
                            value ushr 1
                        }
                }
                value
            }
    }
}
