package app.logdate.feature.editor.audio.extraction

import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Desktop/JVM implementation of AmplitudeExtractor.
 */
class DesktopAmplitudeExtractor : AmplitudeExtractor {
    override suspend fun extractAmplitudes(
        uri: String,
        targetSampleCount: Int,
    ): List<Float> {
        return withContext(Dispatchers.IO) {
            val file = resolveFile(uri) ?: return@withContext emptyList()
            if (!file.exists()) {
                Napier.w { "Audio file not found: ${file.absolutePath}" }
                return@withContext emptyList()
            }

            try {
                AudioSystem.getAudioInputStream(file).use { input ->
                    val baseFormat = input.format
                    val format = ensurePcmSigned(baseFormat)
                    val decodedStream =
                        if (format == baseFormat) {
                            input
                        } else {
                            AudioSystem.getAudioInputStream(format, input)
                        }

                    decodedStream.use { stream ->
                        val bytes = stream.readBytes()
                        val samples = extractMonoSamples(bytes, format)
                        val downsampled = downsampleToTarget(samples, targetSampleCount)
                        normalizeAmplitudes(downsampled)
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to extract amplitudes from ${file.absolutePath}", e)
                emptyList()
            }
        }
    }

    private fun resolveFile(uri: String): File? =
        if (uri.startsWith("file://")) {
            runCatching { File(java.net.URI(uri)) }.getOrNull()
        } else {
            File(uri)
        }

    private fun ensurePcmSigned(format: AudioFormat): AudioFormat =
        if (format.encoding == AudioFormat.Encoding.PCM_SIGNED) {
            format
        } else {
            AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                format.sampleRate,
                16,
                format.channels,
                format.channels * 2,
                format.sampleRate,
                false,
            )
        }

    private fun extractMonoSamples(
        bytes: ByteArray,
        format: AudioFormat,
    ): List<Float> {
        val sampleSizeBytes = format.sampleSizeInBits / 8
        val channels = format.channels
        val frameSize = format.frameSize
        if (sampleSizeBytes <= 0 || frameSize <= 0 || bytes.isEmpty()) {
            return emptyList()
        }

        val totalFrames = bytes.size / frameSize
        val samples = ArrayList<Float>(totalFrames)
        var offset = 0
        repeat(totalFrames) {
            var channelSum = 0.0
            repeat(channels) { channel ->
                val sampleOffset = offset + channel * sampleSizeBytes
                val sample = readSample(bytes, sampleOffset, sampleSizeBytes, format.isBigEndian)
                channelSum += sample
            }
            val average = (channelSum / channels).toFloat()
            samples.add(abs(average))
            offset += frameSize
        }
        return samples
    }

    private fun readSample(
        bytes: ByteArray,
        offset: Int,
        sampleSizeBytes: Int,
        isBigEndian: Boolean,
    ): Int {
        if (sampleSizeBytes == 1) {
            return bytes[offset].toInt()
        }
        val low: Int
        val high: Int
        if (isBigEndian) {
            high = bytes[offset].toInt()
            low = bytes[offset + 1].toInt()
        } else {
            low = bytes[offset].toInt()
            high = bytes[offset + 1].toInt()
        }
        return (high shl 8) or (low and 0xFF)
    }

    private fun downsampleToTarget(
        samples: List<Float>,
        targetCount: Int,
    ): List<Float> {
        if (samples.isEmpty()) return emptyList()
        val chunkSize = samples.size / targetCount
        if (chunkSize <= 0) return samples

        return (0 until targetCount).map { index ->
            val start = index * chunkSize
            val end = minOf(start + chunkSize, samples.size)
            val chunk = samples.subList(start, end)
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
