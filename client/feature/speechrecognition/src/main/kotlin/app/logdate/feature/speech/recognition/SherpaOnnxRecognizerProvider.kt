package app.logdate.feature.speech.recognition

import android.content.Context
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlinePunctuation
import com.k2fsa.sherpa.onnx.OnlinePunctuationConfig
import com.k2fsa.sherpa.onnx.OnlinePunctuationModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages the lifecycle of Sherpa-ONNX recognizer and punctuation models.
 *
 * This class owns model extraction (via [SherpaOnnxModelManager]), recognizer
 * initialization, and punctuation initialization. It exposes thin delegates
 * over [OnlineRecognizer] so callers don't need to hold direct references
 * to the native objects.
 */
class SherpaOnnxRecognizerProvider(
    private val context: Context,
) {
    private val modelManager = SherpaOnnxModelManager(context)
    private var recognizer: OnlineRecognizer? = null
    private var punctuation: OnlinePunctuation? = null
    private val initMutex = Mutex()

    suspend fun ensureInitialized() =
        initMutex.withLock {
            if (recognizer != null) return@withLock

            val sttModelPath =
                withContext(Dispatchers.IO) {
                    modelManager.getModelPath()
                } ?: throw IllegalStateException("Sherpa-ONNX STT model not available")

            Napier.d("STT model path: $sttModelPath")

            val config =
                OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig =
                        OnlineModelConfig(
                            transducer =
                                OnlineTransducerModelConfig(
                                    encoder = "$sttModelPath/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
                                    decoder = "$sttModelPath/decoder-epoch-99-avg-1-chunk-16-left-128.onnx",
                                    joiner = "$sttModelPath/joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
                                ),
                            tokens = "$sttModelPath/tokens.txt",
                            numThreads = 2,
                            provider = "cpu",
                        ),
                    endpointConfig =
                        EndpointConfig(
                            rule1 = EndpointRule(false, 2.4f, 0.0f),
                            rule2 = EndpointRule(true, 1.2f, 0.0f),
                            rule3 = EndpointRule(false, 0.0f, 20.0f),
                        ),
                    enableEndpoint = true,
                    decodingMethod = "greedy_search",
                )

            recognizer =
                withContext(Dispatchers.IO) {
                    OnlineRecognizer(assetManager = null, config = config)
                }
            Napier.d("Sherpa-ONNX recognizer loaded from $sttModelPath")

            val punctModelPath =
                withContext(Dispatchers.IO) {
                    modelManager.getPunctuationModelPath()
                }
            if (punctModelPath != null) {
                Napier.d("Punctuation model path: $punctModelPath")
                val punctConfig =
                    OnlinePunctuationConfig(
                        model =
                            OnlinePunctuationModelConfig(
                                cnnBilstm = "$punctModelPath/model.onnx",
                                bpeVocab = "$punctModelPath/bpe.vocab",
                                numThreads = 1,
                                provider = "cpu",
                            ),
                    )
                punctuation =
                    withContext(Dispatchers.IO) {
                        OnlinePunctuation(assetManager = null, config = punctConfig)
                    }
                Napier.d("Sherpa-ONNX punctuation model loaded from $punctModelPath")
            } else {
                Napier.w("Punctuation model not available; transcription will lack punctuation")
            }
        }

    private fun requireRecognizer(): OnlineRecognizer =
        recognizer ?: throw IllegalStateException("Recognizer not initialized; call ensureInitialized() first")

    fun createStream(): OnlineStream = requireRecognizer().createStream()

    fun isReady(stream: OnlineStream): Boolean = requireRecognizer().isReady(stream)

    fun decode(stream: OnlineStream) = requireRecognizer().decode(stream)

    fun getResult(stream: OnlineStream) = requireRecognizer().getResult(stream)

    fun isEndpoint(stream: OnlineStream): Boolean = requireRecognizer().isEndpoint(stream)

    fun reset(stream: OnlineStream) = requireRecognizer().reset(stream)

    fun addPunctuation(text: String): String =
        try {
            val lowered = text.lowercase()
            punctuation?.addPunctuation(lowered) ?: lowered
        } catch (e: Exception) {
            Napier.e("Punctuation model error, returning raw text", e)
            text.lowercase()
        }

    internal fun release() {
        recognizer?.release()
        recognizer = null
        punctuation?.release()
        punctuation = null
    }

    companion object {
        const val SAMPLE_RATE = 16000
    }
}
