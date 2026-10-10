@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media

import app.logdate.client.media.storage.IosCachedPhotoRescuer
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
import platform.Foundation.NSTemporaryDirectory
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

    @Test
    fun `an exported photo is imported into the media library`() =
        runTest {
            val source = "${NSTemporaryDirectory().trimEnd('/')}/photos-export-test.jpg"
            SystemFileSystem.sink(Path(source)).buffered().use { it.write(byteArrayOf(9, 9)) }
            created += source

            val imported = requireNotNull(IosMediaManager(mediaFiles).importIntoLibrary(source, "ABC-IMG 0007.jpg"))
            val importedPath = requireNotNull(mediaFiles.filePath(imported))
            created += importedPath

            assertEquals("$library/ABC-IMG 0007.jpg", importedPath)
            assertEquals("logdate-media://library/ABC-IMG%200007.jpg", mediaFiles.storedReference(imported))
            assertContentEquals(byteArrayOf(9, 9), IosMediaManager(mediaFiles).readMedia(imported).data)
        }

    @Test
    fun `a photo an earlier build stored in the cache is moved into the library`() =
        runTest {
            val cache = "${directories.canonicalPath(NSHomeDirectory())}/Library/Caches/photo-library-renderable"
            fileManager.createDirectoryAtPath(cache, withIntermediateDirectories = true, attributes = null, error = null)
            val cached = "$cache/ABC-IMG 0009.jpg"
            SystemFileSystem.sink(Path(cached)).buffered().use { it.write(byteArrayOf(5)) }
            created += cached

            val rescued = requireNotNull(IosCachedPhotoRescuer(mediaFiles).rescue("file://$cached"))
            created += "$library/ABC-IMG 0009.jpg"

            assertEquals("logdate-media://library/ABC-IMG%200009.jpg", rescued)
            assertContentEquals(byteArrayOf(5), IosMediaManager(mediaFiles).readMedia(rescued).data)
        }

    @Test
    fun `only cached photos that still exist are rescued`() =
        runTest {
            val rescuer = IosCachedPhotoRescuer(mediaFiles)
            val libraryFile = "$library/${createLibraryFile("not-cache.jpg")}"

            assertNull(rescuer.rescue("file://$libraryFile"))
            assertNull(
                rescuer.rescue(
                    "file://${directories.canonicalPath(NSHomeDirectory())}/Library/Caches/photo-library-renderable/missing.jpg",
                ),
            )
            assertNull(rescuer.rescue("ph://ABC/L0/001"))
        }

    @Test
    fun `importing the same photo again keeps the first copy`() =
        runTest {
            val source = "${NSTemporaryDirectory().trimEnd('/')}/photos-export-twice.jpg"
            SystemFileSystem.sink(Path(source)).buffered().use { it.write(byteArrayOf(1)) }
            created += source
            val manager = IosMediaManager(mediaFiles)
            val first = requireNotNull(manager.importIntoLibrary(source, "DEF-IMG 0008.jpg"))
            created += requireNotNull(mediaFiles.filePath(first))
            SystemFileSystem.sink(Path(source)).buffered().use { it.write(byteArrayOf(2)) }

            val second = requireNotNull(manager.importIntoLibrary(source, "DEF-IMG 0008.jpg"))

            assertEquals(first, second)
            assertContentEquals(byteArrayOf(1), manager.readMedia(second).data)
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
