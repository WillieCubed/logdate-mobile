package app.logdate.client.sync

import android.content.ContentResolver
import io.mockk.mockk
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AndroidPhoneAudioStreamOpenerTest {

    private val opener = AndroidPhoneAudioStreamOpener(mockk<ContentResolver>(relaxed = true))

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
    fun `unsupported media ref returns null`() {
        assertNull(opener.open("asset://audio/123"))
    }
}
