package app.logdate.client.domain.restore

import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.use
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArchiveZipEntryNamesTest {
    @Test
    fun `real ZIP entries preserve directories wrapper and UTF8 names with comments`() {
        val names = listOf("Wrapped/", "Wrapped/manifest.json", "Wrapped/data/", "Wrapped/data/日記.json")
        val bytes = realZip(names, "A comment with PK text")
        withZip(bytes) { assertEquals(names, readNames(it)) }
    }

    @Test
    fun `raw duplicates survive enumeration so admission refuses before choosing an entry`() {
        val names = listOf("manifest.json", "data/a.json", "data/b.json")
        val bytes = realZip(names)
        val from = "data/b.json".encodeToByteArray()
        val to = "data/a.json".encodeToByteArray()
        for (at in 0..bytes.size - from.size) {
            if (from.indices.all { bytes[at + it] == from[it] }) to.copyInto(bytes, at)
        }
        withZip(bytes) {
            val raw = readNames(it)
            assertEquals(listOf("manifest.json", "data/a.json", "data/a.json"), raw)
            var documentRead = false
            assertFailsWith<IllegalArgumentException> {
                RestoreArchiveReader.readBytes(raw) {
                    documentRead = true
                    null
                }
            }
            assertEquals(false, documentRead)
        }
    }

    @Test
    fun `normalization aliases are refused before a ZIP filesystem can hide them`() {
        withZip(realZip(listOf("manifest.json", "data/a.json", "data/../data/a.json"))) {
            var documentRead = false
            assertFailsWith<IllegalArgumentException> {
                RestoreArchiveReader.readBytes(readNames(it)) {
                    documentRead = true
                    null
                }
            }
            assertEquals(false, documentRead)
        }
    }

    @Test
    fun `ZIP64 sentinel records and offsets above four GiB are supported without reading payload`() {
        for (offset in listOf(0L, 0x1_0000_0010L)) {
            val file = File.createTempFile("logdate-zip64-", ".zip")
            try {
                val local = localHeader()
                val centralOffset = offset + local.size
                val central = centralHeader(offset)
                val zip64Offset = centralOffset + central.size
                val end = zip64End(centralOffset, central.size.toLong(), zip64Offset)
                RandomAccessFile(file, "rw").use {
                    it.seek(offset)
                    it.write(local)
                    it.write(central)
                    it.write(end)
                }
                assertEquals(listOf("manifest.json"), readNames(file))
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun `truncation count overflow unsigned overflow and bad central ranges refuse`() {
        val local = localHeader()
        val central = centralHeader(0)
        val end = zip64End(local.size.toLong(), central.size.toLong(), (local.size + central.size).toLong())
        val valid = local + central + end
        val zip64 = local.size + central.size
        val malformed =
            listOf(
                valid.copyOf(valid.size - 1),
                valid.copyOf().also { putLong(it, zip64 + 32, Long.MAX_VALUE) },
                valid.copyOf().also { putLong(it, zip64 + 48, Long.MAX_VALUE) },
                valid.copyOf().also { it[zip64 + 55] = 0x80.toByte() },
                valid.copyOf().also { putLong(it, zip64 + 40, central.size.toLong() - 1) },
                valid.copyOf().also {
                    it[local.size + 30] = 0xff.toByte()
                    it[local.size + 31] = 0x7f
                },
            )
        for (bytes in malformed) withZip(bytes) { assertFailsWith<IllegalArgumentException> { readNames(it) } }
    }

    @Test
    fun `EOCD signature inside a comment cannot redirect directory selection`() {
        val original = realZip(listOf("manifest.json"))
        val fakeEnd = Buffer().writeIntLe(0x06054b50).write(ByteArray(18)).readByteArray()
        original[original.size - 2] = fakeEnd.size.toByte()
        withZip(original + fakeEnd) { assertFailsWith<IllegalArgumentException> { readNames(it) } }
    }

    private fun realZip(
        names: List<String>,
        comment: String? = null,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            if (comment != null) zip.setComment(comment)
            for (name in names) {
                zip.putNextEntry(ZipEntry(name))
                if (!name.endsWith('/')) zip.write("{}".encodeToByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun localHeader(): ByteArray {
        val name = "manifest.json".encodeToByteArray()
        val content = "{}".encodeToByteArray()
        val crc = CRC32().apply { update(content) }.value.toInt()
        return Buffer()
            .writeIntLe(0x04034b50)
            .writeShortLe(45)
            .writeShortLe(0x800)
            .writeShortLe(0)
            .writeIntLe(0)
            .writeIntLe(crc)
            .writeIntLe(content.size)
            .writeIntLe(content.size)
            .writeShortLe(name.size)
            .writeShortLe(0)
            .write(name)
            .write(content)
            .readByteArray()
    }

    private fun centralHeader(offset: Long): ByteArray {
        val name = "manifest.json".encodeToByteArray()
        val crc = CRC32().apply { update("{}".encodeToByteArray()) }.value.toInt()
        return Buffer()
            .writeIntLe(0x02014b50)
            .writeShortLe(45)
            .writeShortLe(45)
            .writeShortLe(0x800)
            .writeShortLe(0)
            .writeIntLe(0)
            .writeIntLe(crc)
            .writeIntLe(-1)
            .writeIntLe(-1)
            .writeShortLe(name.size)
            .writeShortLe(28)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeIntLe(0)
            .writeIntLe(-1)
            .write(name)
            .writeShortLe(1)
            .writeShortLe(24)
            .writeLongLe(2)
            .writeLongLe(2)
            .writeLongLe(offset)
            .readByteArray()
    }

    private fun zip64End(
        offset: Long,
        length: Long,
        zip64Offset: Long,
    ): ByteArray =
        Buffer()
            .writeIntLe(0x06064b50)
            .writeLongLe(44)
            .writeShortLe(45)
            .writeShortLe(45)
            .writeIntLe(0)
            .writeIntLe(0)
            .writeLongLe(1)
            .writeLongLe(1)
            .writeLongLe(length)
            .writeLongLe(offset)
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

    private fun putLong(
        bytes: ByteArray,
        at: Int,
        value: Long,
    ) {
        for (index in 0..7) bytes[at + index] = (value ushr (8 * index)).toByte()
    }

    private fun readNames(file: File): List<String> =
        FileSystem.SYSTEM.openReadOnly(file.absolutePath.toPath()).use { readArchiveZipEntryNames(it) }

    private fun withZip(
        bytes: ByteArray,
        body: (File) -> Unit,
    ) {
        val file = File.createTempFile("logdate-zip-names-", ".zip")
        try {
            file.writeBytes(bytes)
            body(file)
        } finally {
            file.delete()
        }
    }
}
