package app.logdate.client.media.audio.sherpa

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
 * Desktop counterpart of `SherpaOnnxOfflineRecognizerProvider`. Wraps the
 * JVM Sherpa-ONNX Whisper small.en recognizer for the post-recording
 * refinement pass on desktop.
 *
 * The JVM API uses the Builder pattern instead of Kotlin data classes for
 * config — that's the only meaningful surface difference from the Android
 * provider.
 */
internal class DesktopSherpaOfflineRecognizerProvider(
    private val modelManager: DesktopSherpaModelManager,
) {
    private var recognizer: OfflineRecognizer? = null
    private val initMutex = Mutex()

    val isAvailable: Boolean
        get() = modelManager.isWhisperModelReady()

    suspend fun ensureInitialized(): Boolean =
        initMutex.withLock {
            if (recognizer != null) return@withLock true
            val modelPath = modelManager.getWhisperModelPath() ?: return@withLock false

            Napier.d("Desktop Whisper model path: $modelPath")

            val whisperConfig =
                OfflineWhisperModelConfig
                    .builder()
                    .setEncoder("$modelPath/${DesktopSherpaModelManager.WHISPER_ENCODER_NAME}")
                    .setDecoder("$modelPath/${DesktopSherpaModelManager.WHISPER_DECODER_NAME}")
                    .setLanguage("en")
                    .setTask("transcribe")
                    .build()

            val modelConfig =
                OfflineModelConfig
                    .builder()
                    .setWhisper(whisperConfig)
                    .setTokens("$modelPath/${DesktopSherpaModelManager.WHISPER_TOKENS_NAME}")
                    // Desktop has more cores to spare than mobile; four
                    // threads keeps a single inference fast without
                    // saturating the box.
                    .setNumThreads(4)
                    .setProvider("cpu")
                    .build()

            val config =
                OfflineRecognizerConfig
                    .builder()
                    .setFeatureConfig(
                        FeatureConfig
                            .builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setFeatureDim(80)
                            .build(),
                    ).setOfflineModelConfig(modelConfig)
                    .setDecodingMethod("greedy_search")
                    .build()

            recognizer =
                withContext(Dispatchers.IO) {
                    OfflineRecognizer(config)
                }
            Napier.d("Desktop Whisper recognizer loaded")
            true
        }

    /**
     * Transcribes a single audio chunk. The chunk should be at most ~30
     * seconds long — Whisper's context window. Longer audio is segmented by
     * the caller through the VAD before reaching this function.
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
            Napier.e("Error releasing desktop Whisper recognizer", e)
        }
        recognizer = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
