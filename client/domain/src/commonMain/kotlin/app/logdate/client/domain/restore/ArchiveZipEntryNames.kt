package app.logdate.client.domain.restore

import okio.FileHandle

/**
 * Reads only ZIP headers/names before a filesystem can collapse aliases; the caller owns the handle.
 * Metadata is bounded to 250,000 entries, 128 MiB of central records and 32 MiB of raw UTF-8 names.
 * Individual raw names are limited to 4096 UTF-8 bytes and 128 separators before any projection.
 * These metadata limits do not cap media sizes or ZIP64 offsets above 4 GiB.
 */
fun readArchiveZipEntryNames(file: FileHandle): List<String> = readArchiveZipDirectory(file).map { it.name }

internal fun readArchiveZipDirectory(file: FileHandle): List<ArchiveZipEntry> = ArchiveZipNames(file).read()

internal data class ArchiveZipEntry(
    val name: String,
    val method: Int,
    val crc: Long,
    val compressedSize: Long,
    val size: Long,
    val dataOffset: Long,
)

private class ArchiveZipNames(
    private val file: FileHandle,
) {
    private val size = file.size()

    fun read(): List<ArchiveZipEntry> {
        val directory = directory()
        require(directory.count <= MAX_ENTRIES) { "ZIP entry count budget exceeded" }
        require(directory.length <= MAX_CENTRAL_BYTES) { "ZIP central metadata budget exceeded" }
        require(
            rangeEnd(directory.offset, directory.length) == directory.end &&
                directory.count <= Int.MAX_VALUE &&
                directory.count <= directory.length / 46,
        ) { "Invalid ZIP directory bounds" }
        val entries = mutableListOf<ArchiveZipEntry>()
        var cursor = directory.offset
        var nameBytes = 0L
        repeat(directory.count.toInt()) {
            require(rangeEnd(cursor, 46) <= directory.end) { "Truncated ZIP entry" }
            val header = bytes(cursor, 46)
            require(header.u32(0) == 0x02014b50L) { "Invalid ZIP entry header" }
            val nameSize = header.u16(28)
            val extraSize = header.u16(30)
            val commentSize = header.u16(32)
            val next = rangeEnd(cursor, 46L + nameSize + extraSize + commentSize)
            require(next <= directory.end && nameSize in 1..4_096) { "Invalid ZIP entry range" }
            require(nameSize.toLong() <= MAX_NAME_BYTES - nameBytes) { "ZIP filename budget exceeded" }
            nameBytes += nameSize
            val rawName = bytes(cursor + 46, nameSize)
            val name =
                try {
                    rawName.decodeToString(throwOnInvalidSequence = true)
                } catch (failure: Exception) {
                    throw IllegalArgumentException("Invalid ZIP entry UTF-8", failure)
                }
            require('\u0000' !in name && name.count { it == '/' } <= 128) { "Invalid ZIP entry name or depth" }
            val sizes = entrySizes(header, bytes(cursor + 46 + nameSize, extraSize))
            entries += localEntry(header, rawName, name, sizes, directory.offset)
            cursor = next
        }
        require(cursor == directory.end && file.size() == size) { "Inconsistent ZIP directory size" }
        return entries
    }

    private fun directory(): Directory {
        require(size >= 22) { "Truncated ZIP directory" }
        val tailOffset = maxOf(0, size - 65_557)
        val tail = bytes(tailOffset, (size - tailOffset).toInt())
        val signatures = (0..tail.size - 22).filter { tail.u32(it) == 0x06054b50L }
        val candidates = signatures.filter { at -> at + 22 + tail.u16(at + 20) == tail.size }
        require(candidates.size == 1) { "Missing or ambiguous ZIP directory" }
        val at = candidates.single()
        require(at == signatures.last()) { "Ambiguous ZIP consumer directory" }
        val end = tailOffset + at
        require(tail.u16(at + 4) == 0 && tail.u16(at + 6) == 0) { "Spanned ZIP is unsupported" }
        val diskCount = tail.u16(at + 8).toLong()
        val count = tail.u16(at + 10).toLong()
        val length = tail.u32(at + 12)
        val offset = tail.u32(at + 16)
        val locator = if (end >= 20) bytes(end - 20, 20) else null
        return if (locator != null && locator.u32(0) == 0x07064b50L) {
            require(locator.u32(4) == 0L && locator.u32(16) == 1L) { "Spanned ZIP64 is unsupported" }
            val zip64Offset = locator.u64(8)
            val record = bytes(zip64Offset, 56)
            require(record.u32(0) == 0x06064b50L && record.u64(4) >= 44) { "Invalid ZIP64 directory" }
            require(rangeEnd(zip64Offset, rangeEnd(12, record.u64(4))) == end - 20) { "Invalid ZIP64 directory range" }
            require(record.u32(16) == 0L && record.u32(20) == 0L) { "Spanned ZIP64 is unsupported" }
            val actual = Directory(record.u64(32), record.u64(40), record.u64(48), zip64Offset)
            require(record.u64(24) == actual.count) { "Inconsistent ZIP64 counts" }
            require(
                (diskCount == 0xffffL || diskCount == actual.count) &&
                    (count == 0xffffL || count == actual.count) &&
                    (length == 0xffffffffL || length == actual.length) &&
                    (offset == 0xffffffffL || offset == actual.offset),
            ) {
                "Inconsistent ZIP64 directory"
            }
            actual
        } else {
            require(diskCount == count && count != 0xffffL && length != 0xffffffffL && offset != 0xffffffffL) { "Missing ZIP64 directory" }
            Directory(count, length, offset, end)
        }
    }

    private fun entrySizes(
        header: ByteArray,
        extra: ByteArray,
    ): EntrySizes {
        val needsSize = header.u32(24) == 0xffffffffL
        val needsCompressed = header.u32(20) == 0xffffffffL
        val needsOffset = header.u32(42) == 0xffffffffL
        val needsDisk = header.u16(34) == 0xffff
        var zip64: ByteArray? = null
        var cursor = 0
        while (cursor < extra.size) {
            require(extra.size - cursor >= 4) { "Truncated ZIP extra field" }
            val id = extra.u16(cursor)
            val length = extra.u16(cursor + 2)
            require(length <= extra.size - cursor - 4) { "Truncated ZIP extra field" }
            if (id == 1) {
                require(zip64 == null) { "Repeated ZIP64 extra field" }
                zip64 = extra.copyOfRange(cursor + 4, cursor + 4 + length)
            }
            cursor += 4 + length
        }
        if (!needsSize && !needsCompressed && !needsOffset && !needsDisk) {
            require(header.u16(34) == 0) { "Spanned ZIP entry is unsupported" }
            return EntrySizes(header.u32(24), header.u32(20), header.u32(42))
        }
        val values = requireNotNull(zip64) { "Missing ZIP64 entry fields" }
        var position = 0

        fun nextLong(): Long {
            require(values.size - position >= 8) { "Truncated ZIP64 entry field" }
            return values.u64(position).also { position += 8 }
        }
        val size = if (needsSize) nextLong() else header.u32(24)
        val compressed = if (needsCompressed) nextLong() else header.u32(20)
        val offset = if (needsOffset) nextLong() else header.u32(42)
        val disk =
            if (needsDisk) {
                require(values.size - position >= 4) { "Truncated ZIP64 disk field" }
                values.u32(position)
            } else {
                header.u16(34).toLong()
            }
        require(disk == 0L) { "Spanned ZIP entry is unsupported" }
        return EntrySizes(size, compressed, offset)
    }

    private fun localEntry(
        header: ByteArray,
        rawName: ByteArray,
        name: String,
        sizes: EntrySizes,
        centralOffset: Long,
    ): ArchiveZipEntry {
        val flags = header.u16(8)
        val method = header.u16(10)
        require(flags and 0x2041 == 0 && method in listOf(0, 8)) { "Unsupported ZIP entry encryption or method" }
        require(rangeEnd(sizes.offset, 30) <= centralOffset) { "Invalid ZIP local header offset" }
        val local = bytes(sizes.offset, 30)
        require(local.u32(0) == 0x04034b50L && local.u16(6) == flags && local.u16(8) == method) {
            "ZIP local and central headers disagree"
        }
        val localNameSize = local.u16(26)
        val dataOffset = rangeEnd(sizes.offset, 30L + localNameSize + local.u16(28))
        require(
            dataOffset <= centralOffset &&
                localNameSize == rawName.size &&
                bytes(sizes.offset + 30, localNameSize).contentEquals(rawName),
        ) { "ZIP local and central names disagree" }
        require(rangeEnd(dataOffset, sizes.compressed) <= centralOffset) { "ZIP payload exceeds its directory" }
        require(method != 0 || sizes.compressed == sizes.size) { "Invalid stored ZIP entry size" }
        return ArchiveZipEntry(name, method, header.u32(16), sizes.compressed, sizes.size, dataOffset)
    }

    private data class EntrySizes(
        val size: Long,
        val compressed: Long,
        val offset: Long,
    )

    private fun bytes(
        offset: Long,
        count: Int,
    ): ByteArray {
        require(offset >= 0 && count >= 0 && rangeEnd(offset, count.toLong()) <= size) { "Truncated ZIP metadata" }
        val result = ByteArray(count)
        var copied = 0
        while (copied < count) {
            val read = file.read(offset + copied, result, copied, count - copied)
            require(read > 0) { "Truncated ZIP metadata" }
            copied += read
        }
        return result
    }

    private fun rangeEnd(
        offset: Long,
        count: Long,
    ): Long {
        require(offset >= 0 && count >= 0 && offset <= Long.MAX_VALUE - count) { "ZIP range overflow" }
        return offset + count
    }

    private companion object {
        const val MAX_ENTRIES = 250_000L
        const val MAX_CENTRAL_BYTES = 128L * 1024 * 1024
        const val MAX_NAME_BYTES = 32L * 1024 * 1024
    }

    private data class Directory(
        val count: Long,
        val length: Long,
        val offset: Long,
        val end: Long,
    )
}

private fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 255) or ((this[at + 1].toInt() and 255) shl 8)

private fun ByteArray.u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

private fun ByteArray.u64(at: Int): Long {
    val high = u32(at + 4)
    require(high <= Int.MAX_VALUE.toLong()) { "ZIP unsigned value exceeds supported range" }
    return u32(at) or (high shl 32)
}
