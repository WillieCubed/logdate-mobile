package app.logdate.feature.editor.audio.extraction

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs
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

                val decoder = MediaCodec.createDecoderByType(mimeType)
                decoder.configure(format, null, null, 0)
                decoder.start()

                val amplitudes = decodeAndExtractAmplitudes(extractor, decoder, targetSampleCount)

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

    private fun decodeAndExtractAmplitudes(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        targetSampleCount: Int,
    ): List<Float> {
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        val chunkSamples = mutableListOf<Short>()

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
                    extractSamplesFromBuffer(outputBuffer, bufferInfo.size, chunkSamples)
                }
                decoder.releaseOutputBuffer(outputBufferIndex, false)
            }
        }

        return downsampleToTarget(chunkSamples, targetSampleCount)
    }

    private fun extractSamplesFromBuffer(
        buffer: ByteBuffer,
        size: Int,
        samples: MutableList<Short>,
    ) {
        buffer.position(0)
        val shortBuffer = buffer.asShortBuffer()
        val sampleCount = size / 2
        for (i in 0 until sampleCount) {
            samples.add(shortBuffer.get())
        }
    }

    private fun downsampleToTarget(
        samples: List<Short>,
        targetCount: Int,
    ): List<Float> {
        if (samples.isEmpty()) return emptyList()

        val chunkSize = samples.size / targetCount
        if (chunkSize <= 0) return samples.map { abs(it.toFloat()) }

        return (0 until targetCount).map { i ->
            val start = i * chunkSize
            val end = minOf(start + chunkSize, samples.size)
            val chunk = samples.subList(start, end)

            // Compute RMS (Root Mean Square) for better amplitude representation
            val sumSquares = chunk.sumOf { it.toDouble() * it.toDouble() }
            sqrt(sumSquares / chunk.size).toFloat()
        }
    }

    private fun normalizeAmplitudes(amplitudes: List<Float>): List<Float> {
        if (amplitudes.isEmpty()) return emptyList()
        val max = amplitudes.maxOrNull() ?: 1f
        if (max == 0f) return amplitudes.map { 0f }
        return amplitudes.map { (it / max).coerceIn(0f, 1f) }
    }
}
