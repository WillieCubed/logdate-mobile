package app.logdate.client.media

import app.logdate.client.media.storage.AndroidMediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMediaCleanerTest {
    private val filesDir = Files.createTempDirectory("logdate-cleaner").toFile().canonicalFile
    private val cleaner = AndroidMediaCleaner(MediaFileResolver(AndroidMediaDirectories(filesDir)))

    @AfterTest
    fun cleanUp() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `deletes a file named by media reference, file URI or path`() =
        runTest {
            val byReference = file("audio_notes/a b.m4a")
            val byUri = file("media/c.jpg")
            val byPath = file("media/d.jpg")

            cleaner.delete("logdate-media://recordings/a%20b.m4a")
            cleaner.delete(byUri.toURI().toString())
            cleaner.delete(byPath.path)

            assertFalse(byReference.exists() || byUri.exists() || byPath.exists())
        }

    @Test
    fun `leaves what it does not own`() =
        runTest {
            val kept = file("media/kept.jpg")

            cleaner.delete("content://media/external/images/media/1")
            cleaner.delete("https://cloud.logdate.app/media/abc")

            assertTrue(kept.exists())
        }

    private fun file(name: String): File = File(filesDir, name).apply { parentFile.mkdirs() }.also { it.writeText("bytes") }
}
