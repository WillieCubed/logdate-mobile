package app.logdate.wear.sync

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

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `deletes a file in the audio directory`() {
        val file = File(audioDirectory, "recording_1.m4a").apply { writeText("audio") }

        assertTrue(deleteLocalAudio(file.absolutePath, audioDirectory))

        assertFalse(file.exists())
    }

    @Test
    fun `leaves a file outside the audio directory`() {
        val other = File(root, "other.m4a").apply { writeText("audio") }

        assertFalse(deleteLocalAudio(other.absolutePath, audioDirectory))

        assertTrue(other.exists())
    }

    @Test
    fun `leaves a file reached through a parent segment`() {
        val other = File(root, "other.m4a").apply { writeText("audio") }

        assertFalse(deleteLocalAudio("${audioDirectory.absolutePath}/../other.m4a", audioDirectory))

        assertTrue(other.exists())
    }

    @Test
    fun `ignores references that are not local paths`() {
        listOf(null, "", "content://media/external/audio/1", "https://example.test/a.m4a", "recording_1.m4a").forEach { ref ->
            assertFalse(deleteLocalAudio(ref, audioDirectory), "ref=$ref")
        }
    }

    @Test
    fun `reports a missing file as not deleted`() {
        assertEquals(false, deleteLocalAudio(File(audioDirectory, "gone.m4a").absolutePath, audioDirectory))
    }
}
