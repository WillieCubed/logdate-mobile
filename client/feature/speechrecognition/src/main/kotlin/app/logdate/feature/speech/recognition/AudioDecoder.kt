package app.logdate.feature.speech.recognition

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes and resamples codec frames without retaining native-rate PCM or boxing each sample. */
class AudioDecoder(private val context: Context) {
    /** Compatibility path for callers that explicitly require a complete contiguous recording. */
    fun decodeToMono16kHz(uri: String): FloatArray? =
        try {
            runBlocking {
                val chunks = mutableListOf<FloatArray>()
                var sampleCount = 0
                decodeMono16kHz(uri).collect { chunk ->
                    sampleCount = Math.addExact(sampleCount, chunk.size)
                    chunks += chunk
                }
                val output = FloatArray(sampleCount)
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(output, offset)
                    offset += chunk.size
                }
                output
            }
        } catch (e: Exception) {
            Napier.e("Could not decode recorded audio")
            null
        }

    /** Emits mono 16 kHz PCM one codec frame at a time; cancellation releases the codec and extractor. */
    fun decodeMono16kHz(uri: String): Flow<FloatArray> =
        flow {
            val extractor = MediaExtractor()
            try {
                openExtractor(extractor, uri)
                val trackIndex = selectAudioTrack(extractor) ?: error("No audio track")
                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: error("No audio format"))
                try {
                    codec.configure(format, null, null, 0)
                    codec.start()
                    var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    var resampler = MonoPcmResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                    var encoding = AudioFormat.ENCODING_PCM_16BIT
                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false
                    var decodedSamples = false
                    while (!outputDone) {
                        currentCoroutineContext().ensureActive()
                        if (!inputDone) inputDone = queueNextFrame(codec, extractor)
                        val outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            check(!decodedSamples) { "Audio format changed during decoding" }
                            val outputFormat = codec.outputFormat
                            channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            resampler = MonoPcmResampler(outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                            encoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        } else if (outIndex >= 0) {
                            val chunk =
                                try {
                                    val output = codec.getOutputBuffer(outIndex)
                                    if (output != null && info.size > 0) {
                                        decodedSamples = true
                                        resampler.append(readMonoFrame(output, info, channelCount, encoding))
                                    } else {
                                        floatArrayOf()
                                    }
                                } finally {
                                    codec.releaseOutputBuffer(outIndex, false)
                                }
                            if (chunk.isNotEmpty()) emit(chunk)
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        }
                    }
                    val tail = resampler.finish()
                    if (tail.isNotEmpty()) emit(tail)
                } finally {
                    runCatching { codec.stop() }
                    codec.release()
                }
            } finally {
                extractor.release()
            }
        }

    private fun queueNextFrame(codec: MediaCodec, extractor: MediaExtractor): Boolean {
        val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (index < 0) return false
        val input = checkNotNull(codec.getInputBuffer(index))
        val size = extractor.readSampleData(input, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    private fun openExtractor(extractor: MediaExtractor, uri: String) {
        when {
            uri.startsWith("content://") -> extractor.setDataSource(context, Uri.parse(uri), null)
            uri.startsWith("file://") -> extractor.setDataSource(checkNotNull(Uri.parse(uri).path))
            else -> extractor.setDataSource(uri)
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return index
        }
        return null
    }

    private fun readMonoFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo, channels: Int, encoding: Int): FloatArray {
        require(channels > 0)
        val sampleBytes =
            when (encoding) {
                AudioFormat.ENCODING_PCM_16BIT -> 2
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> error("Unsupported decoded PCM encoding")
            }
        require(info.size % (sampleBytes * channels) == 0)
        val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        pcm.position(info.offset)
        pcm.limit(info.offset + info.size)
        return FloatArray(info.size / sampleBytes / channels) {
            var sum = 0f
            repeat(channels) { sum += if (sampleBytes == 2) pcm.short / 32768f else pcm.float }
            sum / channels
        }
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
        private const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
