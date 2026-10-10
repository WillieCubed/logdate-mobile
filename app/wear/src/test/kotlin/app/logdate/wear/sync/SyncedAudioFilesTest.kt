package app.logdate.wear.sync

import app.logdate.client.media.storage.AndroidMediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [deleteLocalAudio], which removes the audio file of a note the phone deleted.
 */
class SyncedAudioFilesTest {
    private val root = Files.createTempDirectory("wear-synced-audio").toFile()
    private val audioDirectory = File(root, "audio_notes").apply { mkdirs() }
    private val mediaFiles = MediaFileResolver(AndroidMediaDirectories(root))

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `deletes a file in the audio directory`() {
        val file = File(audioDirectory, "recording_1.m4a").apply { writeText("audio") }

        assertTrue(deleteLocalAudio(file.absolutePath, mediaFiles))

        assertFalse(file.exists())
    }

    @Test
    fun `deletes a recording named by media reference`() {
        val file = File(audioDirectory, "recording 2.m4a").apply { writeText("audio") }

        assertTrue(deleteLocalAudio("logdate-media://recordings/recording%202.m4a", mediaFiles))

        assertFalse(file.exists())
    }

    @Test
    fun `leaves a file outside the audio directory`() {
        val other = File(root, "other.m4a").apply { writeText("audio") }

        assertFalse(deleteLocalAudio(other.absolutePath, mediaFiles))

        assertTrue(other.exists())
    }

    @Test
    fun `leaves a file reached through a parent segment`() {
        val other = File(root, "other.m4a").apply { writeText("audio") }

        assertFalse(deleteLocalAudio("${audioDirectory.absolutePath}/../other.m4a", mediaFiles))

        assertTrue(other.exists())
    }

    @Test
    fun `ignores references that are not local paths`() {
        listOf(null, "", "content://media/external/audio/1", "https://example.test/a.m4a", "recording_1.m4a").forEach { ref ->
            assertFalse(deleteLocalAudio(ref, mediaFiles), "ref=$ref")
        }
    }

    @Test
    fun `reports a missing file as not deleted`() {
        assertEquals(false, deleteLocalAudio(File(audioDirectory, "gone.m4a").absolutePath, mediaFiles))
    }
}
