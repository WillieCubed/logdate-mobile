@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media

import app.logdate.client.media.storage.IosMediaDirectories
import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.media.storage.fileUrl
import app.logdate.client.media.storage.playableUrl
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** iOS media readers open `logdate-media://` references and files that moved with the app container. */
class IosMediaReferenceReadingTest {
    private val directories = IosMediaDirectories()
    private val mediaFiles = MediaFileResolver(directories)
    private val fileManager = NSFileManager.defaultManager
    private val library = directories.directory(MediaCollection.Library)
    private val created = mutableListOf<String>()

    @AfterTest
    fun removeCreatedFiles() {
        created.forEach { fileManager.removeItemAtPath(it, error = null) }
    }

    @Test
    fun `media manager reads a library reference`() =
        runTest {
            val payload = MediaPayload("IMG 0003.jpg", "image/jpeg", 3, byteArrayOf(7, 8, 9))
            val manager = IosMediaManager(mediaFiles)
            val saved = manager.saveMedia(payload)
            created += requireNotNull(mediaFiles.filePath(saved))
            val reference = mediaFiles.storedReference(saved)

            assertTrue(reference.startsWith("logdate-media://library/"), reference)
            assertTrue(manager.exists(reference))
            assertContentEquals(payload.data, manager.readMedia(reference).data)
            assertContentEquals(
                payload.data,
                manager
                    .openMedia(reference)
                    .open()
                    .buffered()
                    .use { it.readByteArray() },
            )
        }

    @Test
    fun `media manager finds a file that moved with the app container`() =
        runTest {
            val name = createLibraryFile("moved IMG 0004.jpg")
            val stale = staleFileUri("$library/$name")
            val manager = IosMediaManager(mediaFiles)

            assertTrue(manager.exists(stale))
            assertContentEquals(byteArrayOf(4), manager.readMedia(stale).data)
        }

    @Test
    fun `media manager deletes a library file named by reference`() =
        runTest {
            val name = createLibraryFile("delete-me.jpg")
            val manager = IosMediaManager(mediaFiles)

            assertTrue(manager.deleteOwnedMedia("logdate-media://library/delete-me.jpg"))
            assertFalse(fileManager.fileExistsAtPath("$library/$name"))
        }

    @Test
    fun `media manager refuses to delete a photo library asset`() =
        runTest {
            assertFalse(IosMediaManager(mediaFiles).deleteOwnedMedia("ph://ABC/L0/001"))
        }

    @Test
    fun `cleaner deletes a file named by reference or by a path from an earlier container`() =
        runTest {
            val cleaner = IosMediaCleaner(mediaFiles)
            val first = createLibraryFile("cleaner-a.jpg")
            val second = createLibraryFile("cleaner b.jpg")

            cleaner.delete("logdate-media://library/cleaner-a.jpg")
            cleaner.delete(staleFileUri("$library/$second"))

            assertFalse(fileManager.fileExistsAtPath("$library/$first"))
            assertFalse(fileManager.fileExistsAtPath("$library/$second"))
        }

    @Test
    fun `references become file URLs for Apple media frameworks`() {
        assertEquals("$library/IMG 0005.jpg", mediaFiles.fileUrl("logdate-media://library/IMG%200005.jpg")?.path)
        assertEquals("$library/IMG 0005.jpg", mediaFiles.playableUrl("logdate-media://library/IMG%200005.jpg")?.path)
        assertNull(mediaFiles.fileUrl("https://cloud.logdate.app/media/abc"))
        assertEquals("https://cloud.logdate.app/media/abc", mediaFiles.playableUrl("https://cloud.logdate.app/media/abc")?.absoluteString)
        assertNull(mediaFiles.fileUrl("ph://ABC/L0/001"))
    }

    private fun createLibraryFile(name: String): String {
        fileManager.createDirectoryAtPath(library, withIntermediateDirectories = true, attributes = null, error = null)
        SystemFileSystem.sink(Path("$library/$name")).buffered().use { it.write(byteArrayOf(4)) }
        created += "$library/$name"
        return name
    }

    /** [path] as an earlier app container would have spelled it in a percent-encoded file URI. */
    private fun staleFileUri(path: String): String {
        val relative = path.removePrefix("${directories.canonicalPath(NSHomeDirectory())}/")
        return "file:///var/mobile/Containers/Data/Application/9F8E7D6C-OLD/${relative.replace(" ", "%20")}"
    }
}
