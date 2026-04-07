package app.logdate.feature.speech.recognition

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.github.aakira.napier.Napier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Decodes a recorded audio file to mono 16 kHz float PCM, the format expected
 * by Sherpa-ONNX recognizers and audio taggers.
 *
 * This is a CPU-bound, blocking operation; callers should run it on
 * [kotlinx.coroutines.Dispatchers.Default] or similar. The decoder handles
 * the formats produced by Android's [android.media.MediaRecorder] (m4a/AAC)
 * along with anything else [MediaExtractor]/[MediaCodec] can ingest. Multi-
 * channel audio is downmixed to mono; non-16 kHz sources are linearly
 * resampled.
 */
class AudioDecoder(
    private val context: Context,
) {
    /**
     * Reads the audio at [uri] into a single contiguous mono 16 kHz float
     * array, normalized to [-1, 1]. Returns null if the file can't be opened
     * or contains no audio track.
     */
    fun decodeToMono16kHz(uri: String): FloatArray? {
        val extractor = MediaExtractor()
        return try {
            openExtractor(extractor, uri) ?: return null

            val trackIndex = selectAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val rawMonoSamples = drainCodec(extractor, codec, sourceChannelCount)
                resampleTo(rawMonoSamples, sourceSampleRate, TARGET_SAMPLE_RATE)
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        } catch (e: Exception) {
            Napier.e("AudioDecoder failed for $uri", e)
            null
        } finally {
            extractor.release()
        }
    }

    private fun openExtractor(extractor: MediaExtractor, uri: String): Unit? {
        return try {
            if (uri.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(uri), null)
            } else if (uri.startsWith("file://")) {
                extractor.setDataSource(Uri.parse(uri).path ?: return null)
            } else {
                extractor.setDataSource(uri)
            }
        } catch (e: Exception) {
            Napier.e("AudioDecoder could not open $uri", e)
            null
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    /**
     * Pumps the extractor through the codec until end-of-stream, downmixing
     * any multi-channel output to mono float samples in [-1, 1]. Codec output
     * is 16-bit PCM little-endian.
     */
    private fun drainCodec(
        extractor: MediaExtractor,
        codec: MediaCodec,
        sourceChannelCount: Int,
    ): FloatArray {
        val info = MediaCodec.BufferInfo()
        val output = ArrayList<Float>(INITIAL_CAPACITY)
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (outIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outIndex)
                if (outputBuffer != null && info.size > 0) {
                    appendDecodedFrame(outputBuffer, info, sourceChannelCount, output)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }

        return FloatArray(output.size) { output[it] }
    }

    private fun appendDecodedFrame(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        sourceChannelCount: Int,
        sink: ArrayList<Float>,
    ) {
        val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        pcm.position(info.offset)
        pcm.limit(info.offset + info.size)
        val shorts = pcm.asShortBuffer()
        if (sourceChannelCount <= 1) {
            while (shorts.hasRemaining()) {
                sink.add(shorts.get() / 32768.0f)
            }
        } else {
            // Downmix interleaved channels to mono by averaging frame-by-frame
            val frameTemp = ShortArray(sourceChannelCount)
            while (shorts.remaining() >= sourceChannelCount) {
                shorts.get(frameTemp)
                var sum = 0
                for (sample in frameTemp) sum += sample
                sink.add((sum.toFloat() / sourceChannelCount) / 32768.0f)
            }
        }
    }

    /**
     * Linear resampling. Adequate for the speech/ambient-sound use case where
     * the source is typically already 16 kHz or a small integer multiple, and
     * the downstream models are tolerant of mild artifacts.
     */
    private fun resampleTo(
        source: FloatArray,
        sourceRate: Int,
        targetRate: Int,
    ): FloatArray {
        if (sourceRate == targetRate || source.isEmpty()) return source
        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        val outLen = (source.size / ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcIndex = i * ratio
            val lower = srcIndex.toInt()
            val upper = min(lower + 1, source.size - 1)
            val frac = (srcIndex - lower).toFloat()
            out[i] = source[lower] * (1f - frac) + source[upper] * frac
        }
        return out
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val INITIAL_CAPACITY = 16_000 * 30 // ~30s of mono audio
    }
}
