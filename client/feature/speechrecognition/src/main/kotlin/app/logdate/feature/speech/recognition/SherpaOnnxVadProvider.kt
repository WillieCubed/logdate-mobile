package app.logdate.feature.speech.recognition

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages the lifecycle of a Silero VAD instance.
 *
 * Voice Activity Detection runs ahead of the speech recognizer to skip silence,
 * eliminate hallucinated tokens during pauses, and reduce CPU load. Speech segments
 * emitted by the VAD are forwarded to the recognizer downstream.
 *
 * Silero VAD expects audio in fixed [WINDOW_SIZE] sample chunks at [SAMPLE_RATE]Hz.
 */
class SherpaOnnxVadProvider(
    private val context: Context,
) {
    private val modelManager = SherpaOnnxModelManager(context)
    private var vad: Vad? = null
    private val initMutex = Mutex()

    suspend fun ensureInitialized() =
        initMutex.withLock {
            if (vad != null) return@withLock

            val modelPath =
                withContext(Dispatchers.IO) {
                    modelManager.getVadModelPath()
                } ?: throw IllegalStateException("Silero VAD model not available")

            Napier.d("VAD model path: $modelPath")

            val config =
                VadModelConfig(
                    sileroVadModelConfig =
                        SileroVadModelConfig(
                            model = modelPath,
                            threshold = 0.5f,
                            minSilenceDuration = 0.5f,
                            minSpeechDuration = 0.25f,
                            windowSize = WINDOW_SIZE,
                            maxSpeechDuration = 30.0f,
                        ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                    provider = "cpu",
                )

            vad =
                withContext(Dispatchers.IO) {
                    Vad(assetManager = null, config = config)
                }
            Napier.d("Silero VAD loaded")
        }

    private fun requireVad(): Vad = vad ?: throw IllegalStateException("VAD not initialized; call ensureInitialized() first")

    /**
     * Feeds raw PCM samples to the VAD. Samples must be normalized to [-1, 1].
     *
     * Silero VAD expects exactly [WINDOW_SIZE] samples per call, so this method
     * chunks the input into window-sized pieces and feeds them sequentially.
     * Any trailing samples shorter than [WINDOW_SIZE] are buffered until enough
     * data arrives or [flush] is called.
     */
    fun acceptWaveform(samples: FloatArray) {
        if (samples.isEmpty()) return
        val v = requireVad()

        var offset = 0
        // If we have a partial buffer, top it up first
        if (pendingBufferLen > 0) {
            val needed = WINDOW_SIZE - pendingBufferLen
            val available = samples.size.coerceAtMost(needed)
            System.arraycopy(samples, 0, pendingBuffer, pendingBufferLen, available)
            pendingBufferLen += available
            offset += available
            if (pendingBufferLen == WINDOW_SIZE) {
                v.acceptWaveform(pendingBuffer)
                pendingBufferLen = 0
            }
        }

        // Feed full windows
        while (offset + WINDOW_SIZE <= samples.size) {
            val window = FloatArray(WINDOW_SIZE)
            System.arraycopy(samples, offset, window, 0, WINDOW_SIZE)
            v.acceptWaveform(window)
            offset += WINDOW_SIZE
        }

        // Buffer any remainder
        val remainder = samples.size - offset
        if (remainder > 0) {
            System.arraycopy(samples, offset, pendingBuffer, 0, remainder)
            pendingBufferLen = remainder
        }
    }

    /**
     * Flushes any pending buffered samples (zero-padded) and finalizes any
     * in-progress speech segment. Call when audio capture stops.
     */
    fun flush() {
        val v = vad ?: return
        if (pendingBufferLen > 0) {
            // Zero-pad the trailing partial window
            for (i in pendingBufferLen until WINDOW_SIZE) {
                pendingBuffer[i] = 0f
            }
            v.acceptWaveform(pendingBuffer)
            pendingBufferLen = 0
        }
        v.flush()
    }

    fun isEmpty(): Boolean = requireVad().empty()

    fun front(): SpeechSegment = requireVad().front()

    fun pop() = requireVad().pop()

    fun reset() {
        pendingBufferLen = 0
        vad?.reset()
    }

    fun release() {
        try {
            vad?.release()
        } catch (e: Exception) {
            Napier.e("Error releasing VAD", e)
        }
        vad = null
        pendingBufferLen = 0
    }

    private val pendingBuffer = FloatArray(WINDOW_SIZE)
    private var pendingBufferLen = 0

    companion object {
        const val SAMPLE_RATE = 16000
        const val WINDOW_SIZE = 512
    }
}
