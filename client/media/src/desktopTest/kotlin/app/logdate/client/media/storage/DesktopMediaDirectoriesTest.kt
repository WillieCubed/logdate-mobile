package app.logdate.client.media.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopMediaDirectoriesContractTest : MediaDirectoriesContract() {
    private val dataRoot = Files.createTempDirectory("logdate-desktop").toFile()

    @AfterTest
    fun removeDataRoot() {
        dataRoot.deleteRecursively()
    }

    override fun createDirectories(): MediaDirectories = DesktopMediaDirectories(File(dataRoot, ".logdate"))
}

class DesktopMediaDirectoriesTest {
    private val home = Files.createTempDirectory("logdate-home").toFile().canonicalFile
    private val dataRoot = File(home, ".logdate")
    private val directories = DesktopMediaDirectories(dataRoot)

    @AfterTest
    fun removeHome() {
        home.deleteRecursively()
    }

    @Test
    fun `collections live in the LogDate folder in the home directory`() {
        val userHome = File(System.getProperty("user.home")).canonicalPath.replace('\\', '/')

        assertEquals("$userHome/.logdate/media", DesktopMediaDirectories().directory(MediaCollection.Library))
        assertEquals("$userHome/.logdate/audio_notes", DesktopMediaDirectories().directory(MediaCollection.Recordings))
    }

    @Test
    fun `files from another home directory map into this one`() {
        assertEquals(
            "${dataRoot.path}/media/a.jpg",
            directories.pathInCurrentInstall("/Users/previous/.logdate/media/a.jpg"),
        )
        assertEquals(
            "${dataRoot.path}/audio_notes/r.wav",
            directories.pathInCurrentInstall("C:/Users/previous/.logdate/audio_notes/r.wav"),
        )
    }

    @Test
    fun `paths outside a LogDate folder are not relocated`() {
        assertNull(directories.pathInCurrentInstall("/Users/previous/Pictures/a.jpg"))
        assertNull(directories.pathInCurrentInstall("/Users/previous/.logdate"))
    }
}
