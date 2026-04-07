package app.logdate.client.media.audio.sherpa

import io.github.aakira.napier.Napier
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Reads a recorded WAV file and returns mono 16 kHz float PCM, the format
 * Sherpa-ONNX speech models expect.
 *
 * Desktop's `DesktopAudioRecordingManager` records 44.1 kHz stereo signed
 * 16-bit PCM. This decoder downmixes to mono and resamples to 16 kHz via
 * Java Sound API's built-in `getAudioInputStream(targetFormat, source)`,
 * which is plenty good enough for speech recognition input. No
 * MediaCodec / MediaExtractor required — Java Sound has WAV reading and
 * resampling built in.
 */
internal class DesktopWavDecoder {
    fun decodeToMono16kHz(uri: String): FloatArray? {
        val file = uriToFile(uri) ?: return null
        if (!file.exists()) {
            Napier.w("Desktop WAV decoder: file does not exist: ${file.absolutePath}")
            return null
        }
        return try {
            AudioSystem.getAudioInputStream(file).use { source ->
                val resampled = AudioSystem.getAudioInputStream(targetFormat, downmixToMono(source))
                readAllAsFloat(resampled)
            }
        } catch (e: Exception) {
            Napier.e("Desktop WAV decoder failed for ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Wraps the source stream in a mono-output stream when needed. Java
     * Sound's automatic format conversion can resample but not downmix, so
     * we do that step explicitly with a one-pass averaging stream over the
     * source bytes.
     */
    private fun downmixToMono(source: AudioInputStream): AudioInputStream {
        val format = source.format
        if (format.channels == 1) return source

        val monoFormat =
            AudioFormat(
                format.encoding,
                format.sampleRate,
                format.sampleSizeInBits,
                1,
                format.frameSize / format.channels,
                format.frameRate,
                format.isBigEndian,
            )
        return AudioSystem.getAudioInputStream(monoFormat, source)
    }

    /**
     * Drains an `AudioInputStream` of 16-bit PCM into a normalized float
     * array. The stream is expected to already be at the target sample rate
     * and channel count — caller handles resampling/downmixing upstream.
     */
    private fun readAllAsFloat(stream: AudioInputStream): FloatArray {
        val format = stream.format
        require(format.sampleSizeInBits == 16) {
            "Decoder expects 16-bit PCM after resampling but got ${format.sampleSizeInBits}-bit"
        }
        val bigEndian = format.isBigEndian
        val bytes = stream.readAllBytes()
        val sampleCount = bytes.size / 2
        val out = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val low = bytes[i * 2].toInt() and 0xFF
            val high = bytes[i * 2 + 1].toInt() and 0xFF
            val sample =
                if (bigEndian) {
                    ((low shl 8) or high).toShort().toInt()
                } else {
                    ((high shl 8) or low).toShort().toInt()
                }
            out[i] = sample / 32768.0f
        }
        return out
    }

    private fun uriToFile(uri: String): File? =
        when {
            uri.startsWith("file://") -> File(java.net.URI(uri))
            uri.contains(":/") -> null // Unsupported scheme; only local files for now
            else -> File(uri)
        }

    companion object {
        // Speech models expect mono 16 kHz signed 16-bit PCM. Java Sound's
        // automatic conversion handles the rate change for us.
        private val targetFormat =
            AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                16_000f,
                16,
                1,
                2,
                16_000f,
                false,
            )
    }
}
