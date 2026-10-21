package app.logdate.client.e2e

import android.media.MediaExtractor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.media.audio.AdtsToM4aRemuxer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.assertFailsWith

/** Checks the retention oracle against real AAC frames and deliberately truncated output. */
@RunWith(AndroidJUnit4::class)
class QuietSaveAudioRetentionTest {
    @Test
    fun completeSourceIsPreservedWithAccurateDuration() =
        withFixture { directory, frame, _ ->
            val source = aacFile(directory, "source", frame, 2_750)
            val saved = File(directory, "saved.m4a")
            assertTrue(AdtsToM4aRemuxer.remuxToM4a(source, saved))
            assertQuietSaveAudioRetained(source, saved, 117_333)
        }

    @Test
    fun missingFramesAreRejectedInALongRecording() =
        withFixture { directory, frame, _ ->
            val source = aacFile(directory, "source", frame, 2_900)
            val truncated = aacFile(directory, "truncated", frame, 2_850)
            val saved = File(directory, "saved.m4a")
            assertTrue(AdtsToM4aRemuxer.remuxToM4a(truncated, saved))
            assertFailsWith<AssertionError> {
                assertQuietSaveAudioRetained(source, saved, 121_600)
            }
        }

    @Test
    fun incorrectResolvedDurationIsRejectedForAnIntactRecording() =
        withFixture { directory, frame, _ ->
            val source = aacFile(directory, "source", frame, 2_900)
            val saved = File(directory, "saved.m4a")
            assertTrue(AdtsToM4aRemuxer.remuxToM4a(source, saved))
            assertFailsWith<AssertionError> {
                assertQuietSaveAudioRetained(source, saved, 120_000)
            }
        }

    @Test
    fun changedAudioIsRejectedDespiteMatchingFrameCountAndDuration() =
        withFixture { directory, frame, alternate ->
            assertTrue("Fixture needs distinct encoded audio", !frame.contentEquals(alternate))
            val source = aacFile(directory, "source", frame, 2_900)
            val replaced = aacFile(directory, "replaced", alternate, 2_900)
            val saved = File(directory, "saved.m4a")
            assertTrue(AdtsToM4aRemuxer.remuxToM4a(replaced, saved))
            val failure =
                assertFailsWith<AssertionError> {
                    assertQuietSaveAudioRetained(source, saved, 123_733)
                }
            assertTrue(failure.message.orEmpty().contains("Saved AAC frames changed"))
        }

    private fun withFixture(block: (File, ByteArray, ByteArray) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "quiet-save-retention").apply { mkdirs() }
        val fixture = File(directory, "fixture.m4a")
        try {
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .context.assets
                .open("recovery/completed-recording.m4a")
                .use { input -> fixture.outputStream().use(input::copyTo) }
            val extractor = MediaExtractor()
            val frames =
                try {
                    extractor.setDataSource(fixture.absolutePath)
                    extractor.selectTrack(0)
                    List(2) {
                        val buffer = ByteBuffer.allocate(8_192)
                        val size = extractor.readSampleData(buffer, 0)
                        check(size > 0)
                        ByteArray(size).also {
                            buffer.get(it)
                            extractor.advance()
                        }
                    }
                } finally {
                    extractor.release()
                }
            block(directory, frames[0], frames[1])
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun aacFile(
        directory: File,
        name: String,
        frame: ByteArray,
        count: Int,
    ): File {
        val length = frame.size + 7
        // AAC-LC, 24 kHz, mono; one 1024-sample access unit per ADTS frame.
        val header =
            byteArrayOf(
                0xff.toByte(),
                0xf1.toByte(),
                0x58,
                (0x40 or (length shr 11)).toByte(),
                (length shr 3).toByte(),
                (((length and 7) shl 5) or 0x1f).toByte(),
                0xfc.toByte(),
            )
        return File(directory, "$name.aac").apply {
            outputStream().buffered().use { output ->
                repeat(count) {
                    output.write(header)
                    output.write(frame)
                }
            }
        }
    }
}
