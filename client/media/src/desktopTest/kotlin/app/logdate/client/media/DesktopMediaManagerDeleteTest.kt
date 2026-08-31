package app.logdate.client.media

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The safety property behind reclaiming an entry's media: LogDate may delete the copies it made,
 * and nothing else. A file the user picked from their own disk is referenced by an entry exactly
 * the same way, so the only thing separating the two is where it lives.
 */
class DesktopMediaManagerDeleteTest {
    private val tempRoot: Path = Files.createTempDirectory("logdate-media-test")
    private val mediaRoot: Path = tempRoot.resolve("media").also { it.createDirectories() }
    private val manager = DesktopMediaManager(mediaRoot = mediaRoot)

    @AfterTest
    fun cleanUp() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `deletes a file inside the media store`() =
        runTest {
            val owned = mediaRoot.resolve("owned.jpg").apply { writeText("bytes") }

            assertTrue(manager.deleteOwnedMedia(owned.toUri().toString()))
            assertFalse(owned.exists())
        }

    @Test
    fun `refuses a file the user owns elsewhere on disk`() =
        runTest {
            val usersOwnPhoto = tempRoot.resolve("holiday.jpg").apply { writeText("precious") }

            assertFalse(manager.deleteOwnedMedia(usersOwnPhoto.toUri().toString()))
            assertTrue(usersOwnPhoto.exists(), "a file outside the media store must survive")
        }

    @Test
    fun `refuses a symlink that escapes the media store`() =
        runTest {
            val outside = tempRoot.resolve("outside.jpg").apply { writeText("precious") }
            val link = mediaRoot.resolve("looks-owned.jpg")
            Files.createSymbolicLink(link, outside)

            assertFalse(manager.deleteOwnedMedia(link.toUri().toString()))
            assertTrue(outside.exists(), "a symlink target outside the store must survive")
        }

    @Test
    fun `reports false for a file that is already gone`() =
        runTest {
            val missing = mediaRoot.resolve("missing.jpg")

            assertFalse(manager.deleteOwnedMedia(missing.toUri().toString()))
        }

    @Test
    fun `reports false for something that is not a file`() =
        runTest {
            val directory = mediaRoot.resolve("a-directory").also { it.createDirectories() }

            assertFalse(manager.deleteOwnedMedia(directory.toUri().toString()))
            assertTrue(directory.exists())
        }
}
