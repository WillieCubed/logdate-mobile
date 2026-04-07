package app.logdate.feature.speech.recognition

import android.content.Context
import app.logdate.client.media.audio.tagging.AudioTaggingResult
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.tagging.DetectedSound
import com.k2fsa.sherpa.onnx.AudioEvent
import com.k2fsa.sherpa.onnx.AudioTagging
import com.k2fsa.sherpa.onnx.AudioTaggingConfig
import com.k2fsa.sherpa.onnx.AudioTaggingModelConfig
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * On-device audio tagging service backed by Sherpa-ONNX's CED (Consistent
 * Ensemble Distillation) model. Detects ambient sounds — birds, traffic,
 * music, rain, etc. — from the AudioSet ontology.
 *
 * The model is downloaded on demand and not bundled with the app. While the
 * model is missing, [isAvailable] is false and [tagAudio] returns
 * [AudioTaggingResult.Unavailable] without doing any work.
 *
 * Tagging runs in fixed [WINDOW_SECONDS]-second windows over the recording
 * with [WINDOW_OVERLAP_SECONDS] overlap, emitting cumulative results as each
 * window completes so the UI can fade chips in progressively rather than
 * waiting for the full file to finish processing.
 *
 * The "Speech" class is filtered out of detections so the chips reflect what
 * was happening *around* the user, not the speech itself — that's what the
 * transcription pipeline is for.
 */
class SherpaOnnxAudioTaggingService(
    private val context: Context,
    private val audioDecoder: AudioDecoder,
) : AudioTaggingService {
    private val modelManager = SherpaOnnxModelManager(context)
    private var tagging: AudioTagging? = null
    private val initMutex = Mutex()

    override val isAvailable: Boolean
        get() = modelManager.isAudioTaggingModelReady()

    override suspend fun warmUp(): Boolean = ensureInitialized()

    private suspend fun ensureInitialized(): Boolean =
        initMutex.withLock {
            if (tagging != null) return@withLock true
            val modelPath = modelManager.getAudioTaggingModelPath() ?: return@withLock false

            Napier.d("CED audio tagging model path: $modelPath")
            val config =
                AudioTaggingConfig(
                    model =
                        AudioTaggingModelConfig(
                            ced = "$modelPath/${SherpaOnnxModelManager.TAGGING_MODEL_FILE_NAME}",
                            // CED is single-threaded internally; one worker thread keeps it
                            // from contending with the streaming recognizer if both run.
                            numThreads = 1,
                            debug = false,
                            provider = "cpu",
                        ),
                    labels = "$modelPath/${SherpaOnnxModelManager.TAGGING_LABELS_FILE_NAME}",
                    topK = TOP_K,
                )

            tagging =
                withContext(Dispatchers.IO) {
                    AudioTagging(assetManager = null, config = config)
                }
            Napier.d("Sherpa-ONNX audio tagging loaded")
            true
        }

    override fun tagAudio(audioUri: String): Flow<AudioTaggingResult> =
        flow {
            if (!ensureInitialized()) {
                emit(AudioTaggingResult.Unavailable)
                return@flow
            }
            val tagger = tagging ?: run {
                emit(AudioTaggingResult.Unavailable)
                return@flow
            }

            val samples = audioDecoder.decodeToMono16kHz(audioUri)
            if (samples == null || samples.isEmpty()) {
                emit(AudioTaggingResult.Error("Could not decode audio at $audioUri"))
                return@flow
            }

            try {
                val cumulative = mutableMapOf<String, DetectedSound>()
                val windowSamples = WINDOW_SECONDS * AudioDecoder.TARGET_SAMPLE_RATE
                val strideSamples = (WINDOW_SECONDS - WINDOW_OVERLAP_SECONDS) * AudioDecoder.TARGET_SAMPLE_RATE
                var startSample = 0

                while (startSample < samples.size) {
                    if (!currentCoroutineContext().isActive) return@flow
                    val endSample = min(startSample + windowSamples, samples.size)
                    val windowSize = endSample - startSample
                    if (windowSize < MIN_WINDOW_SAMPLES) break

                    val window = samples.copyOfRange(startSample, endSample)
                    val events = tagWindow(tagger, window)
                    val windowStartMs = (startSample.toLong() * 1000) / AudioDecoder.TARGET_SAMPLE_RATE
                    val windowDurationMs = (windowSize.toLong() * 1000) / AudioDecoder.TARGET_SAMPLE_RATE

                    for (event in events) {
                        if (event.prob < MIN_CONFIDENCE) continue
                        if (event.name.equals("Speech", ignoreCase = true)) continue
                        mergeDetection(cumulative, event.name, event.prob, windowStartMs, windowDurationMs)
                    }

                    val isFinalWindow = endSample == samples.size
                    emit(
                        AudioTaggingResult.Success(
                            sounds = cumulative.values.sortedByDescending { it.confidence },
                            isFinal = isFinalWindow,
                        ),
                    )

                    if (isFinalWindow) break
                    startSample += strideSamples
                }
            } catch (e: Exception) {
                Napier.e("Audio tagging failed for $audioUri", e)
                emit(AudioTaggingResult.Error("Audio tagging failed", e))
            }
        }.flowOn(Dispatchers.Default)

    private fun tagWindow(tagger: AudioTagging, window: FloatArray): Array<AudioEvent> {
        val stream = tagger.createStream()
        try {
            stream.acceptWaveform(window, AudioDecoder.TARGET_SAMPLE_RATE)
            return tagger.compute(stream, TOP_K)
        } finally {
            stream.release()
        }
    }

    /**
     * Merges a fresh detection of [name] into the cumulative map. If the same
     * sound was already detected in an earlier window, the entry is extended
     * to span both occurrences and the highest confidence wins. Otherwise a
     * new entry is added at the current window position.
     */
    private fun mergeDetection(
        cumulative: MutableMap<String, DetectedSound>,
        name: String,
        confidence: Float,
        windowStartMs: Long,
        windowDurationMs: Long,
    ) {
        val existing = cumulative[name]
        if (existing == null) {
            cumulative[name] = DetectedSound(
                name = name,
                confidence = confidence,
                startMs = windowStartMs,
                durationMs = windowDurationMs,
            )
        } else {
            val newEnd = windowStartMs + windowDurationMs
            val mergedEnd = maxOf(existing.startMs + existing.durationMs, newEnd)
            cumulative[name] = existing.copy(
                confidence = maxOf(existing.confidence, confidence),
                durationMs = mergedEnd - existing.startMs,
            )
        }
    }

    override fun release() {
        try {
            tagging?.release()
        } catch (e: Exception) {
            Napier.e("Error releasing audio tagging", e)
        }
        tagging = null
    }

    companion object {
        // Five-second windows balance latency (results stream in quickly) against
        // model accuracy (CED-small was trained on 10s clips, but produces useful
        // results from shorter windows for the dominant sound classes we care about).
        private const val WINDOW_SECONDS = 5
        private const val WINDOW_OVERLAP_SECONDS = 1
        private const val MIN_WINDOW_SAMPLES = 16_000 // 1s minimum to avoid garbage
        private const val MIN_CONFIDENCE = 0.3f
        private const val TOP_K = 5
    }
}
