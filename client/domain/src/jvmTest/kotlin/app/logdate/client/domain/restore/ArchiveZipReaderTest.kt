package app.logdate.client.domain.restore

import okio.Buffer
import okio.FileHandle
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArchiveZipReaderTest {
    @Test
    fun `real Java deflated ZIP with descriptors directories and UTF8 reads exact bytes`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("Wrapped/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Wrapped/日記.json"))
            zip.write("{\"text\":\"日記\"}".encodeToByteArray())
            zip.closeEntry()
        }
        withFile { file ->
            file.writeBytes(output.toByteArray())
            FileSystem.SYSTEM.openReadOnly(file.absolutePath.toPath()).use { handle ->
                val zip = ArchiveZipReader(handle)
                assertEquals(listOf("Wrapped/", "Wrapped/日記.json"), zip.entryNames)
                assertTrue(zip.containsFile("Wrapped/日記.json"))
                assertEquals(false, zip.containsFile("Wrapped/"))
                assertEquals("{\"text\":\"日記\"}", zip.source("Wrapped/日記.json").buffer().use { it.readUtf8() })
            }
        }
    }

    @Test
    fun `stored and deflated partial ZIP64 sentinels read through the actual consumer`() {
        for (method in listOf(0, 8)) {
            for (fields in listOf(
                Fields(true, false, false),
                Fields(false, true, false),
                Fields(false, false, true),
                Fields(true, true, false),
                Fields(true, true, true),
            )) {
                withFile { file ->
                    writeZip64(file, method, fields)
                    FileSystem.SYSTEM.openReadOnly(file.absolutePath.toPath()).use { handle ->
                        val zip = ArchiveZipReader(handle)
                        assertEquals(listOf("manifest.json"), zip.entryNames)
                        assertEquals("{}", zip.source("manifest.json").buffer().use { it.readUtf8() })
                    }
                }
            }
        }
    }

    @Test
    fun `CRC length and truncated compressed data fail at consumption without a successful result`() {
        for (defect in listOf("crc", "size", "compressed")) {
            withFile { file ->
                val offsets = writeZip64(file, 8, Fields(false, false, false))
                RandomAccessFile(file, "rw").use {
                    val at =
                        offsets.central +
                            when (defect) {
                                "crc" -> 16
                                "size" -> 24
                                else -> 20
                            }
                    it.seek(at)
                    val value =
                        when (defect) {
                            "crc" -> 0
                            "size" -> 3
                            else -> offsets.compressed - 1
                        }
                    it.write(Buffer().writeIntLe(value).readByteArray())
                }
                FileSystem.SYSTEM.openReadOnly(file.absolutePath.toPath()).use { handle ->
                    val zip = ArchiveZipReader(handle)
                    assertFails { zip.source("manifest.json").buffer().use { it.readByteArray() } }
                }
            }
        }
    }

    @Test
    fun `local name method and flags disagreement is refused before source publication`() {
        for (field in listOf("name", "method", "flags")) {
            withFile { file ->
                val offsets = writeZip64(file, 8, Fields(false, false, false))
                RandomAccessFile(file, "rw").use {
                    it.seek(
                        offsets.local +
                            when (field) {
                                "name" -> 30
                                "method" -> 8
                                else -> 6
                            },
                    )
                    it.writeByte(
                        if (field == "name") {
                            'X'.code
                        } else if (field == "flags") {
                            1
                        } else {
                            0
                        },
                    )
                }
                FileSystem.SYSTEM.openReadOnly(file.absolutePath.toPath()).use { handle ->
                    assertFailsWith<IllegalArgumentException> { ArchiveZipReader(handle) }
                }
            }
        }
    }

    @Test
    fun `later false EOCD signature cannot make admission and consumer choose different directories`() {
        withFile { file ->
            val offsets = writeZip64(file, 0, Fields(false, false, false))
            RandomAccessFile(file, "rw").use {
                it.seek(it.length() - 2)
                it.write(byteArrayOf(23, 0))
                it.seek(it.length())
                it.write(
                    Buffer()
                        .writeIntLe(0x06054b50)
                        .write(ByteArray(18))
                        .writeByte(1)
                        .readByteArray(),
                )
            }
            assertTrue(offsets.central > offsets.local)
            FileSystem.SYSTEM.openReadOnly(file.absolutePath.toPath()).use { handle ->
                assertFailsWith<IllegalArgumentException> { ArchiveZipReader(handle) }
            }
        }
    }

    @Test
    fun `unsupported encryption methods and excessive raw path depth refuse before payload access`() {
        for (field in listOf("flags", "method")) {
            withFile { file ->
                val offsets = writeZip64(file, 8, Fields(false, false, false))
                RandomAccessFile(file, "rw").use {
                    val value = if (field == "flags") 1 else 12
                    it.seek(offsets.local + if (field == "flags") 6 else 8)
                    it.writeByte(value)
                    it.seek(offsets.central + if (field == "flags") 8 else 10)
                    it.writeByte(value)
                }
                FileSystem.SYSTEM.openReadOnly(file.absolutePath.toPath()).use { handle ->
                    assertFailsWith<IllegalArgumentException> { ArchiveZipReader(handle) }
                }
            }
        }
        withFile { file ->
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("a/".repeat(129) + "x"))
                zip.closeEntry()
            }
            file.writeBytes(output.toByteArray())
            FileSystem.SYSTEM.openReadOnly(file.absolutePath.toPath()).use { handle ->
                assertFailsWith<IllegalArgumentException> { ArchiveZipReader(handle) }
            }
        }
    }

    @Test
    fun `inflation cannot read a byte beyond the admitted compressed range`() {
        withFile { file ->
            val offsets = writeZip64(file, 8, Fields(false, false, false))
            RandomAccessFile(file, "rw").use {
                it.seek(offsets.central + 20)
                it.write(Buffer().writeIntLe(offsets.compressed - 1).readByteArray())
            }
            ObservedHandle(FileSystem.SYSTEM.openReadOnly(file.absolutePath.toPath())).use { handle ->
                val zip = ArchiveZipReader(handle)
                handle.observe = true
                assertFails { zip.source("manifest.json").buffer().use { it.readByteArray() } }
                assertTrue(handle.maximumEnd > offsets.local)
                assertTrue(handle.maximumEnd <= offsets.central - 1, "Inflater must not fetch the excluded final compressed byte")
            }
        }
    }

    private class ObservedHandle(
        private val delegate: FileHandle,
    ) : FileHandle(false) {
        var observe = false
        var maximumEnd = 0L

        override fun protectedRead(
            fileOffset: Long,
            array: ByteArray,
            arrayOffset: Int,
            byteCount: Int,
        ): Int {
            if (observe) maximumEnd = maxOf(maximumEnd, fileOffset + byteCount)
            return delegate.read(fileOffset, array, arrayOffset, byteCount)
        }

        override fun protectedSize(): Long = delegate.size()

        override fun protectedClose() = delegate.close()

        override fun protectedWrite(
            fileOffset: Long,
            array: ByteArray,
            arrayOffset: Int,
            byteCount: Int,
        ): Unit = error("Read only")

        override fun protectedFlush(): Unit = error("Read only")

        override fun protectedResize(size: Long): Unit = error("Read only")
    }

    private data class Fields(
        val size: Boolean,
        val compressed: Boolean,
        val offset: Boolean,
    )

    private data class Offsets(
        val local: Long,
        val central: Long,
        val compressed: Int,
    )

    private fun writeZip64(
        file: File,
        method: Int,
        fields: Fields,
    ): Offsets {
        val name = "manifest.json".encodeToByteArray()
        val content = "{}".encodeToByteArray()
        val payload =
            if (method == 0) {
                content
            } else {
                val output = ByteArrayOutputStream()
                val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
                try {
                    DeflaterOutputStream(output, deflater).use { it.write(content) }
                    output.toByteArray()
                } finally {
                    deflater.end()
                }
            }
        val localOffset = if (fields.offset) 0x1_0000_0010L else 128L
        val crc = CRC32().apply { update(content) }.value.toInt()
        val local =
            Buffer()
                .writeIntLe(0x04034b50)
                .writeShortLe(45)
                .writeShortLe(0x800)
                .writeShortLe(method)
                .writeIntLe(0)
                .writeIntLe(crc)
                .writeIntLe(payload.size)
                .writeIntLe(content.size)
                .writeShortLe(name.size)
                .writeShortLe(0)
                .write(name)
                .write(payload)
                .readByteArray()
        val values = Buffer()
        if (fields.size) values.writeLongLe(content.size.toLong())
        if (fields.compressed) values.writeLongLe(payload.size.toLong())
        if (fields.offset) values.writeLongLe(localOffset)
        return writeRecords(file, method, fields, name, content, payload, localOffset, crc, local, values)
    }

    private fun writeRecords(
        file: File,
        method: Int,
        fields: Fields,
        name: ByteArray,
        content: ByteArray,
        payload: ByteArray,
        localOffset: Long,
        crc: Int,
        local: ByteArray,
        values: Buffer,
    ): Offsets {
        val extra = Buffer()
        if (values.size > 0) {
            extra.writeShortLe(1).writeShortLe(values.size.toInt())
            extra.writeAll(values)
        }
        val centralOffset = localOffset + local.size
        val central =
            Buffer()
                .writeIntLe(0x02014b50)
                .writeShortLe(45)
                .writeShortLe(45)
                .writeShortLe(0x800)
                .writeShortLe(method)
                .writeIntLe(0)
                .writeIntLe(crc)
                .writeIntLe(if (fields.compressed) -1 else payload.size)
                .writeIntLe(if (fields.size) -1 else content.size)
                .writeShortLe(name.size)
                .writeShortLe(extra.size.toInt())
                .writeShortLe(0)
                .writeShortLe(0)
                .writeShortLe(0)
                .writeIntLe(0)
                .writeIntLe(if (fields.offset) -1 else localOffset.toInt())
                .write(name)
                .also { it.writeAll(extra) }
                .readByteArray()
        val zip64Offset = centralOffset + central.size
        val end =
            Buffer()
                .writeIntLe(0x06064b50)
                .writeLongLe(44)
                .writeShortLe(45)
                .writeShortLe(45)
                .writeIntLe(0)
                .writeIntLe(0)
                .writeLongLe(1)
                .writeLongLe(1)
                .writeLongLe(central.size.toLong())
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
        RandomAccessFile(file, "rw").use {
            it.seek(localOffset)
            it.write(local)
            it.write(central)
            it.write(end)
        }
        return Offsets(localOffset, centralOffset, payload.size)
    }

    private fun withFile(body: (File) -> Unit) {
        val file = File.createTempFile("logdate-zip-consumer-", ".zip")
        try {
            body(file)
        } finally {
            file.delete()
        }
    }
}
