package app.logdate.client.domain.export

import okio.Path.Companion.toPath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipFile

class ZipArchiveWriterTest {
    private val writer = ZipArchiveWriter()

    @Test
    fun `writer creates portable archive with json and media entries`() {
        val tempDir = Files.createTempDirectory("zip-archive-writer-test")
        try {
            val mediaPath = tempDir.resolve("sample-image.jpg")
            val mediaBytes = byteArrayOf(1, 2, 3, 4, 5)
            Files.write(mediaPath, mediaBytes)

            val archivePath = tempDir.resolve("export.zip")
            writer.write(
                archivePath.toString().toPath(),
                listOf(
                    ZipArchiveEntry.Bytes("metadata.json", """{"version":"1.2"}""".encodeToByteArray()),
                    ZipArchiveEntry.Bytes("notes.json", """{"notes":[]}""".encodeToByteArray()),
                    ZipArchiveEntry.File("media/2026/03/sample-image.jpg", mediaPath.toString().toPath()),
                ),
            )

            ZipFile(archivePath.toFile()).use { zipFile ->
                val entryNames =
                    zipFile
                        .entries()
                        .asSequence()
                        .map { it.name }
                        .toSet()
                assertEquals(
                    setOf("metadata.json", "notes.json", "media/2026/03/sample-image.jpg"),
                    entryNames,
                )

                val metadata =
                    zipFile
                        .getInputStream(zipFile.getEntry("metadata.json"))
                        .use { it.readBytes().decodeToString() }
                assertEquals("""{"version":"1.2"}""", metadata)

                val archivedMedia =
                    zipFile
                        .getInputStream(zipFile.getEntry("media/2026/03/sample-image.jpg"))
                        .use { it.readBytes() }
                assertArrayEquals(mediaBytes, archivedMedia)
                assertTrue(zipFile.getEntry("media/2026/03/sample-image.jpg").size > 0)
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `writer fails when a referenced source file is missing`() {
        val tempDir = Files.createTempDirectory("zip-archive-writer-missing-file")
        try {
            val archivePath = tempDir.resolve("export.zip")
            writer.write(
                archivePath.toString().toPath(),
                listOf(
                    ZipArchiveEntry.File(
                        path = "media/missing.jpg",
                        sourcePath = tempDir.resolve("missing.jpg").toString().toPath(),
                    ),
                ),
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
