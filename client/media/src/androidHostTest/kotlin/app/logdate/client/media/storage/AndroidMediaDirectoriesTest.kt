package app.logdate.client.media.storage

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidMediaDirectoriesContractTest : MediaDirectoriesContract() {
    private val dataDir = Files.createTempDirectory("logdate-data").toFile()

    @AfterTest
    fun removeDataDir() {
        dataDir.deleteRecursively()
    }

    override fun createDirectories(): MediaDirectories = AndroidMediaDirectories(File(dataDir, "files"))
}

class AndroidMediaDirectoriesTest {
    private val dataDir = Files.createTempDirectory("logdate-data").toFile().canonicalFile
    private val filesDir = File(dataDir, "files")
    private val directories = AndroidMediaDirectories(filesDir)

    @AfterTest
    fun removeDataDir() {
        dataDir.deleteRecursively()
    }

    @Test
    fun `library is the media folder in app files`() {
        assertEquals("$filesDir/media", directories.directory(MediaCollection.Library))
    }

    @Test
    fun `recordings are the audio notes folder in app files`() {
        assertEquals("$filesDir/audio_notes", directories.directory(MediaCollection.Recordings))
    }

    @Test
    fun `canonical store objects are library references`() =
        runBlocking {
            val stored = AndroidCanonicalMediaStore(filesDir) {}.store("photo".byteInputStream(), "image/jpeg", 5)
            val ref = MediaFileResolver(directories).storedReference(stored)

            assertTrue(ref.startsWith("logdate-media://library/objects/sha256/"), ref)
        }

    @Test
    fun `files from another Android user or data path map into this install`() {
        val appPackage = "studio.hypertext.logdate"

        assertEquals(
            "$dataDir/files/media/a.jpg",
            directories.pathInCurrentInstall("/data/user/10/$appPackage/files/media/a.jpg"),
        )
        assertEquals(
            "$dataDir/files/audio_notes/r.m4a",
            directories.pathInCurrentInstall("/data/data/$appPackage/files/audio_notes/r.m4a"),
        )
        assertEquals(
            "$dataDir/files/media/a.jpg",
            directories.pathInCurrentInstall("/data/user_de/0/co.reasonabletech.logdate/files/media/a.jpg"),
        )
    }

    @Test
    fun `paths outside app data are not relocated`() {
        assertNull(directories.pathInCurrentInstall("/storage/emulated/0/DCIM/a.jpg"))
        assertNull(directories.pathInCurrentInstall("/data/user/0/studio.hypertext.logdate"))
        assertNull(directories.pathInCurrentInstall("/data/local/tmp/a.jpg"))
    }

    @Test
    fun `aliases of app storage are canonicalized`() {
        val alias = Files.createSymbolicLink(File(dataDir, "alias").toPath(), filesDir.apply { mkdirs() }.toPath()).toFile()

        assertEquals("$filesDir/media/a.jpg", directories.canonicalPath("$alias/media/a.jpg"))
    }
}
