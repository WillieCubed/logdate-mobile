package app.logdate.client.sync

import android.content.ContentResolver
import app.logdate.client.media.storage.AndroidMediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for [AndroidPhoneAudioStreamOpener], facilitating the retrieval of audio content
 * from the phone's local storage for synchronization.
 *
 * The test suite validates the resolution of local file paths into readable byte streams,
 * ensuring that the synchronization engine can reliably access recorded media assets
 * before they are transmitted to the server or shared with other devices.
 */
class AndroidPhoneAudioStreamOpenerTest {

    private val filesDir = createTempDirectory().toFile()
    private val opener =
        AndroidPhoneAudioStreamOpener(
            mockk<ContentResolver>(relaxed = true),
            MediaFileResolver(AndroidMediaDirectories(filesDir)),
        )

    @Test
    fun `absolute file path opens local file bytes`() {
        val payload = "file-audio".encodeToByteArray()
        val tempDir = createTempDirectory()
        val audioFile = tempDir.resolve("audio.m4a")
        audioFile.writeBytes(payload)

        val stream = opener.open(audioFile.toAbsolutePath().toString())

        assertNotNull(stream)
        assertContentEquals(payload, stream.readBytes())
    }

    @Test
    fun `media reference opens the recording it names`() {
        val payload = "recorded-audio".encodeToByteArray()
        File(filesDir, "audio_notes").mkdirs()
        File(filesDir, "audio_notes/wear note.m4a").writeBytes(payload)

        val stream = opener.open("logdate-media://recordings/wear%20note.m4a")

        assertNotNull(stream)
        assertContentEquals(payload, stream.readBytes())
    }

    @Test
    fun `single-slash file URI from the canonical media store opens the file`() {
        val payload = "synced-audio".encodeToByteArray()
        val audioFile = File(filesDir, "media/objects/sha256/ab/abcd.m4a").apply { parentFile.mkdirs() }
        audioFile.writeBytes(payload)

        val stream = opener.open(audioFile.toURI().toString())

        assertNotNull(stream)
        assertContentEquals(payload, stream.readBytes())
    }

    @Test
    fun `unsupported media ref returns null`() {
        assertNull(opener.open("asset://audio/123"))
    }
}
