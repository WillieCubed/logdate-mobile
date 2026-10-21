package app.logdate.client.media.audio

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Reads the complete AAC-LC frames written by the recording service without stopping its encoder. */
internal class AdtsRecordingProgress(
    source: File,
) : Closeable {
    private val reader = RandomAccessFile(source, "r")
    private val header = ByteArray(7)
    private var offset = 0L
    private var samples = 0L
    private var sampleRate: Int? = null

    fun capturedDurationMs(): Long {
        val available = reader.length()
        if (available < offset) throw IOException("The encoded recording was truncated")
        while (available - offset >= header.size) {
            reader.seek(offset)
            reader.readFully(header)
            val frame = header.map { it.toInt() and 0xff }
            if (frame[0] != 0xff || frame[1] and 0xf6 != 0xf0 || frame[2] shr 6 != 1) {
                throw IOException("Expected an AAC-LC ADTS frame")
            }
            val rate = SAMPLE_RATES.getOrNull((frame[2] shr 2) and 15) ?: throw IOException("Unsupported AAC sample rate")
            if (sampleRate != null && rate != sampleRate) throw IOException("The AAC sample rate changed")
            val size = ((frame[3] and 3) shl 11) or (frame[4] shl 3) or (frame[5] shr 5)
            val headerSize = if (frame[1] and 1 == 0) 9 else 7
            if (size <= headerSize) throw IOException("Invalid AAC frame length")
            if (size > available - offset) break
            sampleRate = rate
            samples += 1_024L * ((frame[6] and 3) + 1)
            offset += size
        }
        return sampleRate?.let { samples * 1_000 / it } ?: 0L
    }

    override fun close() = reader.close()

    private companion object {
        val SAMPLE_RATES = intArrayOf(96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350)
    }
}
