package app.logdate.feature.core.export

import app.logdate.client.domain.export.archive.ArchivePath
import kotlinx.coroutines.test.runTest
import okio.buffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopArchiveContainerTest {
    private fun archive(build: (ZipStreamArchiveContainer) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> build(ZipStreamArchiveContainer(zip)) }
        return bytes.toByteArray()
    }

    private fun ZipStreamArchiveContainer.write(
        path: String,
        content: ByteArray,
        compress: Boolean = true,
    ) = entry(ArchivePath.of(path), compress) { sink -> sink.buffer().apply { write(content) }.flush() }

    private fun <T> withZipFile(
        bytes: ByteArray,
        block: (ZipFile) -> T,
    ): T {
        val file = File.createTempFile("archive", ".zip").apply { writeBytes(bytes) }
        try {
            return ZipFile(file).use(block)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `files are readable by streaming and through the central directory`() {
        val bytes =
            archive {
                it.write("README.txt", "hello".encodeToByteArray())
                it.write("media/photos/2026/a.jpg", ByteArray(2048) { 7 }, compress = false)
            }

        val streamed = mutableMapOf<String, Int>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { streamed[it.name] = zip.readBytes().size }
        }
        assertEquals(mapOf("README.txt" to 5, "media/photos/2026/a.jpg" to 2048), streamed)

        withZipFile(bytes) { zip ->
            assertEquals("hello", zip.getInputStream(zip.getEntry("README.txt")).readBytes().decodeToString())
        }
    }

    @Test
    fun `media is not compressed and text is`() {
        val text = "abc".repeat(1000).encodeToByteArray()
        val bytes =
            archive {
                it.write("data/notes.json", text)
                it.write("media/audio/a.m4a", text, compress = false)
            }

        withZipFile(bytes) { zip ->
            assertTrue(zip.getEntry("data/notes.json").compressedSize < text.size / 2, "text should be compressed")
            assertTrue(zip.getEntry("media/audio/a.m4a").compressedSize >= text.size, "media should not be compressed")
        }
    }

    @Test
    fun `a file url and an absolute path are opened but a network address is not`() =
        runTest {
            val file = Files.createTempFile("media", ".jpg").toFile().apply { writeText("bytes") }
            try {
                val opener = DesktopMediaSourceOpener()

                assertEquals("bytes", opener.open(file.absolutePath)?.buffer()?.readUtf8())
                assertEquals("bytes", opener.open(file.toURI().toString())?.buffer()?.readUtf8())
                assertNull(opener.open("https://example.com/a.jpg"))
                assertNull(opener.open("content://media/external/images/media/1"))
                assertNull(opener.open(file.absolutePath + ".gone"))
            } finally {
                file.delete()
            }
        }
}
