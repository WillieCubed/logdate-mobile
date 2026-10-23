package app.logdate.client.domain.restore

import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArchiveZipIntegrityTest {
    @Test
    fun `correct ZIP CRC does not admit changed metadata with a stale sha256 manifest`() {
        val file = Files.createTempFile("archive-integrity-", ".zip").toFile()
        try {
            val files = archiveIntegrityFixture()
            val changed = "{\"journals\":[]} ".encodeToByteArray()
            files["data/journals.json"] = changed
            ZipOutputStream(file.outputStream()).use { zip ->
                files.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            var destinationWrites = 0
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("data/journals.json")
                val crc = CRC32().apply { update(changed) }.value
                assertEquals(crc, entry.crc)
                assertFailsWith<IllegalArgumentException> {
                    val archive =
                        RestoreArchiveReader.readBytes(zip.entries().toList().map { it.name }) { path ->
                            zip.getEntry(path)?.let { zip.getInputStream(it).use { input -> input.readBytes() } }
                        }
                    ArchivePayloadValidator.validate(archive)
                    destinationWrites++
                }
            }
            assertEquals(0, destinationWrites)
        } finally {
            file.delete()
        }
    }
}
