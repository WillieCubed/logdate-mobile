@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package app.logdate.feature.editor.audio.extraction

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderStatusReading
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreMedia.CMBlockBufferCopyDataBytes
import platform.CoreMedia.CMBlockBufferGetDataLength
import platform.CoreMedia.CMSampleBufferGetDataBuffer
import platform.CoreMedia.CMSampleBufferInvalidate
import platform.Foundation.NSError
import platform.Foundation.NSURL

/**
 * iOS implementation of AmplitudeExtractor using AVAssetReader.
 */
class IosAmplitudeExtractor : AmplitudeExtractor {
    override suspend fun extractAmplitudes(
        uri: String,
        targetSampleCount: Int,
    ): List<Float> =
        withContext(Dispatchers.Default) {
            val url =
                if (uri.startsWith("file://")) {
                    NSURL.URLWithString(uri)
                } else {
                    NSURL.fileURLWithPath(uri)
                } ?: run {
                    Napier.w { "Unable to resolve audio URL for $uri" }
                    return@withContext emptyList()
                }

            val asset: AVAsset = AVURLAsset.URLAssetWithURL(url, null)
            val track =
                asset
                    .tracksWithMediaType(AVMediaTypeAudio)
                    .filterIsInstance<AVAssetTrack>()
                    .firstOrNull()
                    ?: run {
                        Napier.w { "No audio track found for $uri" }
                        return@withContext emptyList()
                    }

            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val reader = AVAssetReader(asset, errorPtr.ptr)
                val error = errorPtr.value
                if (error != null) {
                    Napier.w { "Failed to create AVAssetReader for $uri: ${error.localizedDescription}" }
                    return@withContext emptyList()
                }

                val outputSettings: Map<Any?, Any?> =
                    mapOf(
                        AV_FORMAT_ID_KEY to kAudioFormatLinearPCM,
                        AV_LINEAR_PCM_IS_FLOAT_KEY to kCFBooleanTrue,
                        AV_LINEAR_PCM_BIT_DEPTH_KEY to 32,
                        AV_LINEAR_PCM_IS_BIG_ENDIAN_KEY to kCFBooleanFalse,
                    )

                val output = AVAssetReaderTrackOutput(track, outputSettings)
                output.alwaysCopiesSampleData = false
                reader.addOutput(output)

                if (!reader.startReading()) {
                    Napier.w { "Failed to start AVAssetReader for $uri" }
                    return@withContext emptyList()
                }

                val samples = mutableListOf<Float>()
                while (reader.status == AVAssetReaderStatusReading) {
                    val sampleBuffer = output.copyNextSampleBuffer() ?: break
                    val blockBuffer = CMSampleBufferGetDataBuffer(sampleBuffer)
                    if (blockBuffer != null) {
                        val length = CMBlockBufferGetDataLength(blockBuffer).toInt()
                        if (length > 0) {
                            val buffer = ByteArray(length)
                            buffer.usePinned { pinned ->
                                CMBlockBufferCopyDataBytes(
                                    blockBuffer,
                                    0uL,
                                    length.toULong(),
                                    pinned.addressOf(0),
                                )
                            }
                            samples.addAll(unpackFloatSamples(buffer))
                        }
                    }
                    CMSampleBufferInvalidate(sampleBuffer)
                }

                if (samples.isEmpty()) {
                    return@withContext emptyList()
                }

                val downsampled = downsampleToTarget(samples, targetSampleCount)
                normalizeAmplitudes(downsampled)
            }
        }

    private fun unpackFloatSamples(bytes: ByteArray): List<Float> {
        val count = bytes.size / 4
        val samples = ArrayList<Float>(count)
        var offset = 0
        repeat(count) {
            val bits =
                (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            samples.add(kotlin.math.abs(Float.fromBits(bits)))
            offset += 4
        }
        return samples
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
            kotlin.math.sqrt(sumSquares / chunk.size).toFloat()
        }
    }

    private fun normalizeAmplitudes(amplitudes: List<Float>): List<Float> {
        if (amplitudes.isEmpty()) return emptyList()
        val max = amplitudes.maxOrNull() ?: 1f
        if (max == 0f) return amplitudes.map { 0f }
        return amplitudes.map { (it / max).coerceIn(0f, 1f) }
    }
}

private const val AV_FORMAT_ID_KEY = "AVFormatIDKey"
private const val AV_LINEAR_PCM_IS_FLOAT_KEY = "AVLinearPCMIsFloatKey"
private const val AV_LINEAR_PCM_BIT_DEPTH_KEY = "AVLinearPCMBitDepthKey"
private const val AV_LINEAR_PCM_IS_BIG_ENDIAN_KEY = "AVLinearPCMIsBigEndianKey"
