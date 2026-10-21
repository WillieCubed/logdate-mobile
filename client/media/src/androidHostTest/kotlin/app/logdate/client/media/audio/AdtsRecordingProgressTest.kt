package app.logdate.client.media.audio

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdtsRecordingProgressTest {
    // AAC-LC, 44100 Hz, mono, ten-byte ADTS frame with one 1024-sample access unit.
    private val frame = byteArrayOf(0xff.toByte(), 0xf1.toByte(), 0x50, 0x40, 0x01, 0x5f, 0xfc.toByte(), 1, 2, 3)

    @Test
    fun `only complete frames advance capture time as the recording grows`() =
        withSource { source ->
            AdtsRecordingProgress(source).use { progress ->
                assertEquals(0L, progress.capturedDurationMs())
                source.appendBytes(frame.copyOfRange(0, 5))
                assertEquals(0L, progress.capturedDurationMs())
                source.appendBytes(frame.copyOfRange(5, 8))
                assertEquals(0L, progress.capturedDurationMs())
                source.appendBytes(frame.copyOfRange(8, 10))
                assertEquals(23L, progress.capturedDurationMs())
                assertEquals(23L, progress.capturedDurationMs(), "Polling a stalled encoder cannot manufacture captured time")
                source.appendBytes(frame)
                assertEquals(46L, progress.capturedDurationMs())
            }
        }

    @Test
    fun `fractional frame durations accumulate without rounding each frame`() =
        withSource { source ->
            source.outputStream().use { output -> repeat(441) { output.write(frame) } }
            AdtsRecordingProgress(source).use { progress -> assertEquals(10_240L, progress.capturedDurationMs()) }
        }

    @Test
    fun `malformed frames never count as recorded audio`() =
        withSource { source ->
            source.writeBytes(frame.copyOf().apply { this[0] = 0 })
            AdtsRecordingProgress(source).use { progress -> assertFailsWith<IOException> { progress.capturedDurationMs() } }
        }

    @Test
    fun `CRC frames use the stream sample rate and count every raw data block`() =
        withSource { source ->
            // AAC-LC, 48000 Hz, CRC present, twelve-byte frame, two raw data blocks.
            source.writeBytes(byteArrayOf(0xff.toByte(), 0xf0.toByte(), 0x4c, 0x40, 0x01, 0x9f.toByte(), 0xfd.toByte(), 0, 0, 1, 2, 3))
            AdtsRecordingProgress(source).use { progress -> assertEquals(42L, progress.capturedDurationMs()) }
        }

    @Test
    fun `truncating an already counted stream fails closed`() =
        withSource { source ->
            source.writeBytes(frame)
            AdtsRecordingProgress(source).use { progress ->
                assertEquals(23L, progress.capturedDurationMs())
                source.writeBytes(byteArrayOf())
                assertFailsWith<IOException> { progress.capturedDurationMs() }
            }
        }

    private fun withSource(block: (File) -> Unit) {
        val source = Files.createTempFile("adts-progress", ".aac").toFile()
        try {
            block(source)
        } finally {
            source.delete()
        }
    }
}
