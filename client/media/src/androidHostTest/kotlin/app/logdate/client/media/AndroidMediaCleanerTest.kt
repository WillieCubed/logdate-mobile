package app.logdate.client.media

import app.logdate.client.media.storage.AndroidMediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidMediaCleanerTest {
    private val filesDir = Files.createTempDirectory("logdate-cleaner").toFile().canonicalFile
    private val cleaner = AndroidMediaCleaner(MediaFileResolver(AndroidMediaDirectories(filesDir)))

    @AfterTest
    fun cleanUp() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `retains persisted media under every path spelling`() =
        runTest {
            val byReference = file("audio_notes/a b.m4a")
            val byUri = file("media/c.jpg")
            val byPath = file("media/d.jpg")

            cleaner.delete("logdate-media://recordings/a%20b.m4a")
            cleaner.delete(byUri.toURI().toString())
            cleaner.delete(byPath.path)

            assertTrue(byReference.exists() && byUri.exists() && byPath.exists())
        }

    @Test
    fun `leaves what it does not own`() =
        runTest {
            val kept = file("media/kept.jpg")

            cleaner.delete("content://media/external/images/media/1")
            cleaner.delete("https://cloud.logdate.app/media/abc")

            assertTrue(kept.exists())
        }

    @Test
    fun `never deletes a canonical object whose content may be shared`() =
        runTest {
            val digest = "ab".repeat(32)
            val shared = file("media/objects/sha256/ab/$digest.png")

            cleaner.delete(shared.toURI().toString())
            cleaner.delete("logdate-media://library/objects/sha256/ab/$digest.png")

            assertTrue(shared.exists())
        }

    @Test
    fun `refuses an external file and an app path linked to it`() =
        runTest {
            val outside = Files.createTempFile("logdate-external-media-", ".jpg").toFile()
            try {
                outside.writeText("private bytes")
                val link = File(filesDir, "media/external-link.jpg")
                requireNotNull(link.parentFile).mkdirs()
                Files.createSymbolicLink(link.toPath(), outside.toPath())

                cleaner.delete(outside.toURI().toString())
                cleaner.delete(link.toURI().toString())

                assertTrue(outside.exists())
                assertTrue(link.exists())
            } finally {
                outside.delete()
            }
        }

    private fun file(name: String): File = File(filesDir, name).apply { requireNotNull(parentFile).mkdirs() }.also { it.writeText("bytes") }
}
