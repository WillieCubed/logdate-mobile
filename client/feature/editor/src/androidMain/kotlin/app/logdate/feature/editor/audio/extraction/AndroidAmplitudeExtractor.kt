package app.logdate.feature.editor.audio.extraction

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Android implementation of AmplitudeExtractor using MediaExtractor and MediaCodec.
 *
 * Decodes audio files and computes RMS amplitude for each chunk, then
 * downsamples to the target sample count for waveform visualization.
 */
class AndroidAmplitudeExtractor(
    private val context: Context,
) : AmplitudeExtractor {
    override suspend fun extractAmplitudes(
        uri: String,
        targetSampleCount: Int,
    ): List<Float> =
        withContext(Dispatchers.Default) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, Uri.parse(uri), null)

                val audioTrackIndex =
                    findAudioTrack(extractor) ?: run {
                        Napier.w { "No audio track found in $uri" }
                        return@withContext emptyList()
                    }
                extractor.selectTrack(audioTrackIndex)

                val format = extractor.getTrackFormat(audioTrackIndex)
                val mimeType =
                    format.getString(MediaFormat.KEY_MIME) ?: run {
                        Napier.w { "No MIME type found for audio track" }
                        return@withContext emptyList()
                    }
                val durationUs =
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION)
                    } else {
                        -1L
                    }

                val decoder = MediaCodec.createDecoderByType(mimeType)
                decoder.configure(format, null, null, 0)
                decoder.start()

                val amplitudes = decodeAndExtractAmplitudes(extractor, decoder, durationUs, targetSampleCount)

                decoder.stop()
                decoder.release()

                normalizeAmplitudes(amplitudes)
            } catch (e: Exception) {
                Napier.e(e) { "Error extracting amplitudes from $uri" }
                emptyList()
            } finally {
                extractor.release()
            }
        }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return null
    }

    /**
     * Decodes the audio and computes per-bucket RMS amplitudes in a single pass.
     *
     * Each output buffer from MediaCodec is assigned to a waveform bucket using
     * its presentation timestamp, so no intermediate sample list is accumulated.
     * Memory usage is O(targetSampleCount) regardless of audio length.
     */
    private fun decodeAndExtractAmplitudes(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        durationUs: Long,
        targetSampleCount: Int,
    ): List<Float> {
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        // Accumulate RMS per bucket — fixed size, no per-sample boxing
        val sumSquares = DoubleArray(targetSampleCount)
        val counts = IntArray(targetSampleCount)
        var framesProcessed = 0L

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        sawInputEOS = true
                    } else {
                        decoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime,
                            0,
                        )
                        extractor.advance()
                    }
                }
            }

            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
                val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val bucketIndex =
                        if (durationUs > 0 && bufferInfo.presentationTimeUs >= 0) {
                            ((bufferInfo.presentationTimeUs.toDouble() / durationUs) * targetSampleCount)
                                .toInt()
                                .coerceIn(0, targetSampleCount - 1)
                        } else {
                            (framesProcessed % targetSampleCount).toInt()
                        }

                    outputBuffer.position(0)
                    val shortBuffer = outputBuffer.asShortBuffer()
                    val frameCount = bufferInfo.size / 2
                    var bufferSumSquares = 0.0
                    for (i in 0 until frameCount) {
                        val sample = shortBuffer.get().toDouble()
                        bufferSumSquares += sample * sample
                    }
                    sumSquares[bucketIndex] += bufferSumSquares
                    counts[bucketIndex] += frameCount
                    framesProcessed++
                }
                decoder.releaseOutputBuffer(outputBufferIndex, false)
            }
        }

        return (0 until targetSampleCount).map { i ->
            if (counts[i] > 0) sqrt(sumSquares[i] / counts[i]).toFloat() else 0f
        }
    }

    private fun normalizeAmplitudes(amplitudes: List<Float>): List<Float> {
        if (amplitudes.isEmpty()) return emptyList()
        val max = amplitudes.maxOrNull() ?: 1f
        if (max == 0f) return amplitudes.map { 0f }
        return amplitudes.map { (it / max).coerceIn(0f, 1f) }
    }
}
