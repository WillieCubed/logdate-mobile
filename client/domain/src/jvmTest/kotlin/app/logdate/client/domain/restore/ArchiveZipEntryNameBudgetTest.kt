package app.logdate.client.domain.restore

import okio.Buffer
import okio.FileHandle
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArchiveZipEntryNameBudgetTest {
    @Test
    fun `entry count is rejected before allocating or reading any entry name`() {
        SparseDirectory(count = 250_001, length = 250_001L * 46).use { file ->
            val failure = assertFailsWith<IllegalArgumentException> { readArchiveZipEntryNames(file) }
            assertEquals("ZIP entry count budget exceeded", failure.message)
            assertEquals(0L, file.nameBytesRead)
        }
    }

    @Test
    fun `central metadata is rejected before reading entries even with a small count`() {
        SparseDirectory(count = 1, length = 128L * 1024 * 1024 + 1).use { file ->
            val failure = assertFailsWith<IllegalArgumentException> { readArchiveZipEntryNames(file) }
            assertEquals("ZIP central metadata budget exceeded", failure.message)
            assertEquals(0L, file.nameBytesRead)
        }
    }

    @Test
    fun `aggregate UTF8 names stop before allocating or reading beyond the budget`() {
        SparseDirectory(count = 8_193, length = 8_193L * (46 + 4_096), nameSize = 4_096).use { file ->
            val failure = assertFailsWith<IllegalArgumentException> { readArchiveZipEntryNames(file) }
            assertEquals("ZIP filename budget exceeded", failure.message)
            assertTrue(file.nameBytesRead <= 32L * 1024 * 1024)
            assertEquals(8_192L * 4_096, file.nameBytesRead)
            assertTrue(file.maximumRead <= 65_557)
        }
    }

    /** Synthetic metadata regions avoid creating a giant archive or reading any media body. */
    private class SparseDirectory(
        private val count: Long,
        private val length: Long,
        private val nameSize: Int = 1,
    ) : FileHandle(false) {
        private val centralOffset = 30L + nameSize
        private val zip64Offset = centralOffset + length
        private val end =
            Buffer()
                .writeIntLe(0x06064b50)
                .writeLongLe(44)
                .writeShortLe(45)
                .writeShortLe(45)
                .writeIntLe(0)
                .writeIntLe(0)
                .writeLongLe(count)
                .writeLongLe(count)
                .writeLongLe(length)
                .writeLongLe(centralOffset)
                .writeIntLe(0x07064b50)
                .writeIntLe(0)
                .writeLongLe(zip64Offset)
                .writeIntLe(1)
                .writeIntLe(0x06054b50)
                .writeShortLe(0)
                .writeShortLe(0)
                .writeShortLe(0xffff)
                .writeShortLe(0xffff)
                .writeIntLe(-1)
                .writeIntLe(-1)
                .writeShortLe(0)
                .readByteArray()
        private val header =
            Buffer()
                .writeIntLe(0x02014b50)
                .write(ByteArray(24))
                .writeShortLe(nameSize)
                .write(ByteArray(16))
                .readByteArray()
        var nameBytesRead = 0L
        var maximumRead = 0

        override fun protectedSize(): Long = zip64Offset + end.size

        override fun protectedRead(
            fileOffset: Long,
            array: ByteArray,
            arrayOffset: Int,
            byteCount: Int,
        ): Int {
            if (fileOffset == protectedSize()) return -1
            val read = minOf(byteCount.toLong(), protectedSize() - fileOffset).toInt()
            array.fill('a'.code.toByte(), arrayOffset, arrayOffset + read)
            maximumRead = maxOf(maximumRead, byteCount)

            fun overlay(
                offset: Long,
                bytes: ByteArray,
            ) {
                val from = maxOf(fileOffset, offset)
                val to = minOf(fileOffset + read, offset + bytes.size)
                if (from <
                    to
                ) {
                    bytes.copyInto(array, arrayOffset + (from - fileOffset).toInt(), (from - offset).toInt(), (to - offset).toInt())
                }
            }
            overlay(
                0,
                Buffer()
                    .writeIntLe(0x04034b50)
                    .write(ByteArray(22))
                    .writeShortLe(nameSize)
                    .writeShortLe(0)
                    .readByteArray(),
            )
            // Only the bounded aggregate-name test reaches central records.
            if (count <= 8_193 && length <= 128L * 1024 * 1024) {
                val width = 46L + nameSize
                val first = maxOf(0L, (fileOffset - centralOffset) / width)
                val last = minOf(count - 1, (fileOffset + read - centralOffset) / width)
                if (first <= last) for (index in first..last) overlay(centralOffset + index * width, header)
                val relative = fileOffset - centralOffset
                if (relative >= 0 && relative % width == 46L && byteCount == nameSize) nameBytesRead += read
            }
            overlay(zip64Offset, end)
            return read
        }

        override fun protectedWrite(
            fileOffset: Long,
            array: ByteArray,
            arrayOffset: Int,
            byteCount: Int,
        ): Unit = error("Read only")

        override fun protectedFlush(): Unit = error("Read only")

        override fun protectedResize(size: Long): Unit = error("Read only")

        override fun protectedClose() = Unit
    }
}
