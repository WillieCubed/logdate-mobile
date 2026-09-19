package app.logdate.feature.core.export

import kotlinx.coroutines.test.runTest
import okio.buffer
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopMediaSourceOpenerTest {
    private val folder = Files.createTempDirectory("opener").toFile()
    private val opener = DesktopMediaSourceOpener()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun fileNamed(name: String) = File(folder, name).apply { writeText("bytes") }

    @Test
    fun `a file url the app wrote with an unencoded space is opened`() =
        runTest {
            val file = fileNamed("Beach Day.jpg")

            assertEquals("bytes", opener.open("file://${file.absolutePath}")?.buffer()?.readUtf8())
        }

    @Test
    fun `a file url with a percent in the name is opened`() =
        runTest {
            val file = fileNamed("100%.jpg")

            assertEquals("bytes", opener.open("file://${file.absolutePath}")?.buffer()?.readUtf8())
        }

    @Test
    fun `a properly encoded file url is opened`() =
        runTest {
            val file = fileNamed("Beach Day.jpg")

            assertEquals("bytes", opener.open(file.toURI().toString())?.buffer()?.readUtf8())
        }

    @Test
    fun `a windows drive path is a local path`() {
        assertEquals(
            listOf(File("C:\\Users\\Jane\\audio_notes\\recording.wav")),
            localFileCandidates("C:\\Users\\Jane\\audio_notes\\recording.wav"),
        )
        assertEquals(listOf(File("D:/media/a.jpg")), localFileCandidates("D:/media/a.jpg"))
    }

    @Test
    fun `a file url built from a windows path is read as that path`() {
        val candidates = localFileCandidates("file://C:\\Users\\Jane\\media\\Beach Day.jpg")

        assertEquals(File("C:\\Users\\Jane\\media\\Beach Day.jpg"), candidates.last())
    }

    @Test
    fun `network addresses and other schemes are not local files`() =
        runTest {
            assertEquals(emptyList(), localFileCandidates("https://example.com/a.jpg"))
            assertEquals(emptyList(), localFileCandidates("content://media/external/images/media/1"))
            assertNull(opener.open("https://example.com/a.jpg"))
        }

    @Test
    fun `a reference to a missing file is not opened`() =
        runTest {
            assertNull(opener.open("file://${File(folder, "gone.jpg").absolutePath}"))
        }
}
