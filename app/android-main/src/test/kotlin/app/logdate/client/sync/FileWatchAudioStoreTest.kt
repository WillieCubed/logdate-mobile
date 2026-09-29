package app.logdate.client.sync

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests [FileWatchAudioStore], the on-disk home for audio and metadata received from the watch.
 */
class FileWatchAudioStoreTest {
    private val root = Files.createTempDirectory("watch-audio-store").toFile()
    private val audioDirectory = File(root, "audio_notes")
    private val incomingDirectory = File(root, "watch_incoming")
    private val store = FileWatchAudioStore(audioDirectory, incomingDirectory)
    private val noteId = Uuid.random()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `audio path is a deterministic file inside the audio directory`() {
        val path = File(store.audioPath(noteId))

        assertEquals(audioDirectory.canonicalFile, path.parentFile?.canonicalFile)
        assertEquals("wear_$noteId.m4a", path.name)
    }

    @Test
    fun `written audio is readable at the audio path`() = runTest {
        val written = store.writeAudio(noteId, ByteArrayInputStream(byteArrayOf(1, 2, 3)))

        assertTrue(written)
        assertTrue(store.hasAudio(noteId))
        assertContentEquals(byteArrayOf(1, 2, 3), File(store.audioPath(noteId)).readBytes())
    }

    @Test
    fun `a transfer that fails part way leaves no file behind`() = runTest {
        val written = store.writeAudio(noteId, FailingStream(byteArrayOf(1, 2, 3)))

        assertFalse(written)
        assertFalse(store.hasAudio(noteId))
        assertTrue(audioDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a failed transfer keeps the earlier complete file`() = runTest {
        store.writeAudio(noteId, ByteArrayInputStream(byteArrayOf(1, 2, 3)))

        val written = store.writeAudio(noteId, FailingStream(byteArrayOf(9, 9)))

        assertFalse(written)
        assertContentEquals(byteArrayOf(1, 2, 3), File(store.audioPath(noteId)).readBytes())
    }

    @Test
    fun `an empty transfer is not stored`() = runTest {
        val written = store.writeAudio(noteId, ByteArrayInputStream(ByteArray(0)))

        assertFalse(written)
        assertFalse(store.hasAudio(noteId))
    }

    @Test
    fun `stashed metadata round trips`() = runTest {
        val data = mapOf("uid" to noteId.toString(), "jsonPayload" to """{"a":"b"}""")

        store.stashMetadata(noteId, data)

        assertEquals(data, store.stashedMetadata(noteId))
    }

    @Test
    fun `missing metadata reads as null`() = runTest {
        assertNull(store.stashedMetadata(noteId))
    }

    @Test
    fun `corrupt metadata reads as null`() = runTest {
        incomingDirectory.mkdirs()
        File(incomingDirectory, "$noteId.json").writeText("not json")

        assertNull(store.stashedMetadata(noteId))
    }

    @Test
    fun `clearing metadata keeps the audio file`() = runTest {
        store.writeAudio(noteId, ByteArrayInputStream(byteArrayOf(1)))
        store.stashMetadata(noteId, mapOf("uid" to noteId.toString()))

        store.clearMetadata(noteId)

        assertNull(store.stashedMetadata(noteId))
        assertTrue(store.hasAudio(noteId))
    }

    @Test
    fun `discard removes the audio file and the metadata`() = runTest {
        store.writeAudio(noteId, ByteArrayInputStream(byteArrayOf(1)))
        store.stashMetadata(noteId, mapOf("uid" to noteId.toString()))

        store.discard(noteId)

        assertFalse(store.hasAudio(noteId))
        assertNull(store.stashedMetadata(noteId))
    }

    private class FailingStream(
        private val prefix: ByteArray,
    ) : InputStream() {
        private var position = 0

        override fun read(): Int {
            if (position < prefix.size) return prefix[position++].toInt()
            throw IOException("connection lost")
        }
    }
}
