package app.logdate.client.media

import app.logdate.client.media.storage.DesktopMediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Desktop media reading accepts `logdate-media://` references and every legacy file URI spelling. */
class DesktopMediaReferenceReadingTest {
    private val home: Path = Files.createTempDirectory("logdate-home").toRealPath()
    private val library: Path = home.resolve(".logdate/media").also { it.createDirectories() }
    private val manager = DesktopMediaManager(MediaFileResolver(DesktopMediaDirectories(home.resolve(".logdate").toFile())))

    @AfterTest
    fun cleanUp() {
        home.toFile().deleteRecursively()
    }

    @Test
    fun `reads a library reference`() =
        runTest {
            library.resolve("IMG 0001.jpg").writeBytes(byteArrayOf(1, 2))

            assertTrue(manager.exists("logdate-media://library/IMG%200001.jpg"))
            assertContentEquals(byteArrayOf(1, 2), manager.readMedia("logdate-media://library/IMG%200001.jpg").data)
        }

    @Test
    fun `reads unescaped and single-slash file URIs`() =
        runTest {
            val file = library.resolve("IMG 0002.jpg").apply { writeBytes(byteArrayOf(3)) }

            assertContentEquals(byteArrayOf(3), manager.readMedia("file://$file").data)
            assertContentEquals(byteArrayOf(3), manager.readMedia(file.toUri().toString().replaceFirst("file:///", "file:/")).data)
        }

    @Test
    fun `deletes a library file named by reference`() =
        runTest {
            val file = library.resolve("owned.jpg").apply { writeBytes(byteArrayOf(4)) }

            assertTrue(manager.deleteOwnedMedia("logdate-media://library/owned.jpg"))
            assertFalse(file.exists())
        }
}
