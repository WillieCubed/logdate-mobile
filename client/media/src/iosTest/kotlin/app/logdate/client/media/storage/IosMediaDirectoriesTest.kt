@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.logdate.client.media.storage

import app.logdate.client.media.IosMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.media.audio.IosAudioStorage
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosMediaDirectoriesContractTest : MediaDirectoriesContract() {
    override fun createDirectories(): MediaDirectories = IosMediaDirectories()
}

class IosMediaDirectoriesTest {
    private val home = "/var/mobile/Containers/Data/Application/0A1B2C3D-NEW"
    private val directories =
        IosMediaDirectories(
            containerHome = home,
            documentsDirectory = "$home/Documents",
            applicationSupportDirectory = "$home/Library/Application Support",
        )

    @Test
    fun `library is the media folder in Documents`() {
        assertEquals("${systemDirectory(NSDocumentDirectory)}/media", IosMediaDirectories().directory(MediaCollection.Library))
    }

    @Test
    fun `recordings are the audio notes folder in Application Support`() {
        assertEquals(
            "${systemDirectory(NSApplicationSupportDirectory)}/audio_notes",
            IosMediaDirectories().directory(MediaCollection.Recordings),
        )
    }

    @Test
    fun `new recordings are written inside the recordings collection`() =
        runTest {
            val target = IosAudioStorage().createRecordingTarget("m4a")

            assertEquals(
                IosMediaDirectories().directory(MediaCollection.Recordings),
                target.path.substringBeforeLast('/'),
            )
        }

    @Test
    fun `saved media is written inside the library collection`() =
        runTest {
            val resolver = MediaFileResolver(IosMediaDirectories())
            val saved =
                IosMediaManager().saveMedia(
                    MediaPayload(fileName = "IMG 0001.jpg", mimeType = "image/jpeg", sizeBytes = 3, data = byteArrayOf(1, 2, 3)),
                )
            val path = requireNotNull(resolver.filePath(saved))
            try {
                assertEquals(MediaCollection.Library, resolver.refFor(path)?.collection)
            } finally {
                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            }
        }

    @Test
    fun `private var spellings are canonicalized`() {
        assertEquals("$home/Documents/media/a.jpg", directories.canonicalPath("/private$home/Documents/media/a.jpg"))
        assertEquals("/private/tmp/a.jpg", directories.canonicalPath("/private/tmp/a.jpg"))
    }

    @Test
    fun `files from an earlier app container map into this container`() {
        val previous = "/var/mobile/Containers/Data/Application/9F8E7D6C-OLD"

        assertEquals("$home/Documents/media/a.jpg", directories.pathInCurrentInstall("$previous/Documents/media/a.jpg"))
        assertEquals(
            "$home/Library/Application Support/audio_notes/r.m4a",
            directories.pathInCurrentInstall("/private$previous/Library/Application Support/audio_notes/r.m4a"),
        )
        assertEquals(
            "$home/Documents/imports/c.mov",
            directories.pathInCurrentInstall(
                "/Users/me/Library/Developer/CoreSimulator/Devices/D0/data/Containers/Data/Application/OLD/Documents/imports/c.mov",
            ),
        )
    }

    @Test
    fun `paths outside any app container are not relocated`() {
        assertNull(directories.pathInCurrentInstall("/tmp/a.jpg"))
        assertNull(directories.pathInCurrentInstall("/var/mobile/Containers/Data/Application/OLD"))
        assertNull(directories.pathInCurrentInstall("/var/mobile/Containers/Shared/AppGroup/X/a.jpg"))
    }

    @Test
    fun `a file moved with the app container is found and stored as a reference`() {
        val real = IosMediaDirectories()
        val resolver = MediaFileResolver(real)
        val library = real.directory(MediaCollection.Library)
        val name = "container-move-test IMG 0002.jpg"
        NSFileManager.defaultManager.createDirectoryAtPath(library, withIntermediateDirectories = true, attributes = null, error = null)
        NSFileManager.defaultManager.createFileAtPath("$library/$name", contents = null, attributes = null)
        val relativeToHome = "$library/$name".removePrefix("${real.canonicalPath(NSHomeDirectory())}/")
        val stale = "file:///var/mobile/Containers/Data/Application/9F8E7D6C-OLD/${relativeToHome.replace(" ", "%20")}"
        try {
            assertEquals("$library/$name", resolver.filePath(stale))
            assertEquals("logdate-media://library/container-move-test%20IMG%200002.jpg", resolver.storedReference(stale))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath("$library/$name", error = null)
        }
    }

    private fun systemDirectory(directory: ULong): String =
        requireNotNull(
            (
                NSFileManager.defaultManager.URLForDirectory(
                    directory = directory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = true,
                    error = null,
                ) as NSURL?
            )?.path,
        ).let { IosMediaDirectories().canonicalPath(it) }
}
