package app.logdate.client.e2e

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.media.audio.CrashSafeRecording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.math.abs

internal fun assertQuietSaveAudioRetained(
    source: File,
    saved: File,
    resolvedDurationMs: Long,
) {
    val captured = AudioCaptureSnapshot.read(source)
    val retained = AudioCaptureSnapshot.read(saved)
    val evidence = "resolvedMs=$resolvedDurationMs source=$captured saved=$retained"
    assertTrue("The microphone captured no AAC frames: $evidence", captured.frames > 0)
    assertEquals("Saved audio format changed: $evidence", captured.sampleRate, retained.sampleRate)
    assertEquals("Saved audio channels changed: $evidence", captured.channels, retained.channels)
    assertEquals("Saved audio lost AAC frames: $evidence", captured.frames, retained.frames)
    assertEquals("Saved AAC frames changed: $evidence", captured.digest, retained.digest)
    // AAC-LC encodes 1024 PCM samples per access unit; allow only millisecond rounding.
    val expectedDurationMs = captured.frames * 1_024_000L / captured.sampleRate
    assertTrue("Saved duration disagrees with captured samples: $evidence", abs(retained.durationMs - expectedDurationMs) <= 1)
    assertTrue("Resolved duration disagrees with captured samples: $evidence", abs(resolvedDurationMs - expectedDurationMs) <= 1)
}

/** Retains the raw capture inode after finalization removes its staging path. */
internal class QuietSaveDurationEvidence(
    finalFile: File,
    private val started: Long,
    private val scenarioName: String,
) : Closeable {
    private val raw = FileInputStream(CrashSafeRecording.inFlightFile(finalFile, isolateForBackup = true))
    private val progress = CopyOnWriteArrayList<String>()

    @Volatile private var observing = true
    private val observer =
        thread(name = "quiet-save-capture-evidence", isDaemon = true) {
            while (observing) {
                progress += "${SystemClock.elapsedRealtime() - started},${raw.channel.size()}"
                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }

    fun verify(
        finalFile: File,
        resolvedDuration: Long,
    ): String {
        val elapsedMs = SystemClock.elapsedRealtime() - started
        val output = File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), scenarioName)
        output.mkdirs()
        val captured = File(output, "quiet-save-source.aac")
        raw.channel.position(0)
        captured.outputStream().use { raw.copyTo(it) }
        val finalized = File(output, "quiet-save-final.m4a")
        finalFile.copyTo(finalized, overwrite = true)
        val summary =
            buildString {
                appendLine("startToPublicationMs=$elapsedMs")
                appendLine("resolvedDurationMs=$resolvedDuration")
                appendLine("source=${AudioCaptureSnapshot.read(captured)}")
                appendLine("saved=${AudioCaptureSnapshot.read(finalized)}")
            }
        File(output, "quiet-save-duration.txt").writeText(summary)
        File(output, "quiet-save-capture-progress.csv").writeText("startToSampleMs,sourceBytes\n" + progress.joinToString("\n"))
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", summary) })
        assertQuietSaveAudioRetained(captured, finalized, resolvedDuration)
        return summary
    }

    override fun close() {
        observing = false
        observer.interrupt()
        observer.join()
        raw.close()
    }
}

private data class AudioCaptureSnapshot(
    val frames: Int,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
    val digest: String,
) {
    companion object {
        fun read(file: File): AudioCaptureSnapshot {
            val extractor = MediaExtractor()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val duration = checkNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
                extractor.setDataSource(file.absolutePath)
                val track =
                    (0 until extractor.trackCount).first {
                        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
                    }
                val format = extractor.getTrackFormat(track)
                check(format.getInteger(MediaFormat.KEY_AAC_PROFILE) == 2) { "Expected AAC-LC: $format" }
                extractor.selectTrack(track)
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteBuffer.allocate(65_536)
                val sizeBytes = ByteBuffer.allocate(4)
                var frames = 0
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    sizeBytes.clear()
                    sizeBytes.putInt(size)
                    digest.update(sizeBytes.array())
                    buffer.limit(size)
                    digest.update(buffer)
                    frames++
                    if (!extractor.advance()) break
                }
                return AudioCaptureSnapshot(
                    frames,
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    duration,
                    digest.digest().joinToString("") { "%02x".format(it) },
                )
            } finally {
                extractor.release()
                retriever.release()
            }
        }
    }
}
