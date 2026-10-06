package app.logdate.feature.speech.recognition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class StreamingAudioDecoderTest {
    @Test
    fun stereoRecordingDecodesProgressivelyWithEveryResampledFramePreserved() =
        runBlocking {
            withContext(Dispatchers.Default) {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val file = makeRecording(context)
                try {
                    val decoder = AudioDecoder(context)
                    var count = 0
                    var chunks = 0
                    decoder.decodeMono16kHz(file.path).collect { samples ->
                        assertTrue(samples.size < 16_000)
                        assertTrue(samples.all { abs(it - 0.25f) < 0.00001f })
                        count += samples.size
                        chunks++
                    }
                    assertEquals(16_000 * SECONDS, count)
                    assertTrue(chunks > 10)
                    // Early collector cancellation must release codec resources before another decode.
                    decoder.decodeMono16kHz(file.path).take(1).collect { assertTrue(it.isNotEmpty()) }
                    assertEquals(count, decoder.decodeToMono16kHz(file.path)?.size)
                } finally {
                    file.delete()
                }
            }
        }

    private fun makeRecording(context: Context): File {
        val file = File.createTempFile("streaming-decoder-", ".wav", context.cacheDir)
        file.outputStream().buffered().use { output ->
            fun number(value: Int, bytes: Int) = repeat(bytes) { output.write(value ushr (it * 8) and 255) }
            val dataSize = 48_000 * SECONDS * 4
            output.write("RIFF".toByteArray())
            number(36 + dataSize, 4)
            output.write("WAVEfmt ".toByteArray())
            number(16, 4)
            number(1, 2)
            number(2, 2)
            number(48_000, 4)
            number(48_000 * 4, 4)
            number(4, 2)
            number(16, 2)
            output.write("data".toByteArray())
            number(dataSize, 4)
            val frame = byteArrayOf(0, 64, 0, 0)
            repeat(48_000 * SECONDS) { output.write(frame) }
        }
        return file
    }

    companion object {
        private const val SECONDS = 22
    }
}
