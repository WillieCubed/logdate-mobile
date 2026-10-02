package app.logdate.client.media.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.aakira.napier.Napier
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Wraps a raw AAC stream into the MPEG-4 audio file the rest of the app plays and uploads. */
fun interface AudioRemuxer {
    /**
     * @return true when [destination] holds a playable file made from [source]. A false result
     *   leaves no partial [destination] behind.
     */
    fun remuxToM4a(
        source: File,
        destination: File,
    ): Boolean
}

/**
 * File names for a recording that is written as raw AAC and wrapped into MPEG-4 when it ends.
 *
 * An MPEG-4 file keeps its index at the end, so a recording cut short by the process dying cannot
 * be played. Raw AAC frames are self-delimiting, so what was written before the cut still is. The
 * in-flight file sits beside the final one under the same name, which lets a later launch find the
 * recording a dead process left behind.
 */
object CrashSafeRecording {
    const val IN_FLIGHT_EXTENSION = "aac"
    const val FINAL_EXTENSION = "m4a"

    fun inFlightFile(finalFile: File): File = File(finalFile.parentFile, "${finalFile.nameWithoutExtension}.$IN_FLIGHT_EXTENSION")

    fun finalFile(inFlightFile: File): File = File(inFlightFile.parentFile, "${inFlightFile.nameWithoutExtension}.$FINAL_EXTENSION")
}

/** Copies the AAC frames of a raw AAC file into an MPEG-4 container without re-encoding them. */
object AdtsToM4aRemuxer : AudioRemuxer {
    private const val SAMPLE_BUFFER_BYTES = 1 shl 16

    override fun remuxToM4a(
        source: File,
        destination: File,
    ): Boolean {
        val staging = File(destination.parentFile, "${destination.name}.tmp")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            staging.delete()
            extractor.setDataSource(source.absolutePath)
            val track = audioTrack(extractor)
            if (track == null) {
                Napier.w("No audio found in ${source.name}")
                return false
            }
            extractor.selectTrack(track)
            val activeMuxer = MediaMuxer(staging.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = activeMuxer
            val outputTrack = activeMuxer.addTrack(extractor.getTrackFormat(track))
            activeMuxer.start()
            val samples = copySamples(extractor, activeMuxer, outputTrack)
            activeMuxer.stop()
            muxer = null
            activeMuxer.release()
            if (samples == 0) {
                Napier.w("${source.name} holds no audio frames")
                staging.delete()
                return false
            }
            Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            Napier.e("Could not wrap ${source.name} into ${destination.name}", e)
            muxer?.let { runCatching { it.release() } }
            staging.delete()
            false
        } finally {
            extractor.release()
        }
    }

    private fun audioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

    private fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        outputTrack: Int,
    ): Int {
        val buffer = ByteBuffer.allocate(SAMPLE_BUFFER_BYTES)
        val info = MediaCodec.BufferInfo()
        var samples = 0
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
            muxer.writeSampleData(outputTrack, buffer, info)
            samples++
            extractor.advance()
        }
        return samples
    }
}
