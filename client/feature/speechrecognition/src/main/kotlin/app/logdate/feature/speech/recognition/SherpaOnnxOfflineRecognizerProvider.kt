package app.logdate.feature.speech.recognition

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages the lifecycle of the offline Whisper small.en recognizer used for the
 * refinement pass of two-pass transcription.
 *
 * Whisper produces dramatically better accuracy and native punctuation compared
 * to the streaming Zipformer, but it operates on full audio segments rather than
 * a live stream. This provider is used after a recording is captured (or while
 * a captured recording is being polished progressively) to rewrite the streaming
 * output with higher-quality text.
 *
 * The model is **not bundled** with the app — it is downloaded on demand and
 * placed at the path returned by [SherpaOnnxModelManager.getWhisperModelPath].
 * If the model is not available, [isAvailable] returns false and the caller
 * should fall back to streaming-only transcription.
 */
class SherpaOnnxOfflineRecognizerProvider(
    private val context: Context,
) {
    private val modelManager = SherpaOnnxModelManager(context)
    private var recognizer: OfflineRecognizer? = null
    private val initMutex = Mutex()

    /** Whether the Whisper model files are present on device. */
    val isAvailable: Boolean
        get() = modelManager.isWhisperModelReady()

    /**
     * Eagerly loads the Whisper recognizer into memory. Safe to call multiple times.
     * Returns false if the model is not available on device.
     *
     * Call this as soon as the user starts recording so the model is hot by the
     * time the recording stops, eliminating model-load latency from the
     * refinement pipeline.
     */
    suspend fun ensureInitialized(): Boolean =
        initMutex.withLock {
            if (recognizer != null) return@withLock true

            val modelPath = modelManager.getWhisperModelPath() ?: return@withLock false

            Napier.d("Whisper model path: $modelPath")

            val config =
                OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig =
                        OfflineModelConfig(
                            whisper =
                                OfflineWhisperModelConfig(
                                    encoder = "$modelPath/${SherpaOnnxModelManager.WHISPER_ENCODER_NAME}",
                                    decoder = "$modelPath/${SherpaOnnxModelManager.WHISPER_DECODER_NAME}",
                                    language = "en",
                                    task = "transcribe",
                                ),
                            tokens = "$modelPath/${SherpaOnnxModelManager.WHISPER_TOKENS_NAME}",
                            // Whisper benefits from more threads since each utterance is decoded
                            // independently. 4 threads keeps refinement responsive on modern
                            // mid-range Android devices without starving the streaming pass.
                            numThreads = 4,
                            provider = "cpu",
                        ),
                    decodingMethod = "greedy_search",
                )

            recognizer =
                withContext(Dispatchers.IO) {
                    OfflineRecognizer(assetManager = null, config = config)
                }
            Napier.d("Whisper offline recognizer loaded")
            true
        }

    /**
     * Transcribes a single audio chunk. The chunk should be at most ~30 seconds
     * long — Whisper's context window. Longer audio must be pre-segmented (the
     * VAD pipeline does this naturally per utterance).
     *
     * Returns null if the recognizer is not initialized.
     */
    suspend fun transcribe(samples: FloatArray): OfflineRecognizerResult? {
        val r = recognizer ?: return null
        if (samples.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            val stream = r.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                r.decode(stream)
                r.getResult(stream)
            } finally {
                stream.release()
            }
        }
    }

    fun release() {
        try {
            recognizer?.release()
        } catch (e: Exception) {
            Napier.e("Error releasing Whisper recognizer", e)
        }
        recognizer = null
    }

    companion object {
        const val SAMPLE_RATE = 16000

        /**
         * Maximum samples Whisper can process in a single chunk.
         * Whisper has a hard 30-second context window at 16kHz.
         */
        const val MAX_CHUNK_SAMPLES = 30 * SAMPLE_RATE
    }
}
