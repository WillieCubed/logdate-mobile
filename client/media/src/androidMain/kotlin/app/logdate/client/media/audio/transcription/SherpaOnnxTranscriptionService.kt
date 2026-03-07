package app.logdate.client.media.audio.transcription

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device transcription service using Sherpa-ONNX speech recognition with
 * online punctuation.
 *
 * Uses [AudioRecord] (not [MediaRecorder]) to capture raw PCM audio, which does NOT
 * request audio focus — music playback continues uninterrupted. The PCM stream is fed
 * to a Sherpa-ONNX [OnlineRecognizer] for streaming speech-to-text.
 *
 * Finalized segments are run through an [OnlinePunctuation] model to add
 * capitalization and punctuation before being appended to accumulated text.
 */
class SherpaOnnxTranscriptionService(
    private val context: Context,
) : TranscriptionService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)

    private val modelManager = SherpaOnnxModelManager(context)
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var punctuation: OnlinePunctuation? = null
    private var audioRecord: AudioRecord? = null
    private var recognitionJob: Job? = null

    private val accumulatedSegments = mutableListOf<String>()
    private var currentPartial: String = ""

    private val modelInitMutex = Mutex()

    @Volatile
    private var isListening = false

    override suspend fun warmUp() {
        initializeModels()
    }

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = _transcriptionFlow.asSharedFlow()

    override suspend fun startLiveTranscription(): Boolean {
        if (isListening) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Napier.e("RECORD_AUDIO permission not granted for transcription")
            _transcriptionFlow.emit(TranscriptionResult.Error("Microphone permission not granted"))
            return false
        }

        return try {
            // Start capturing audio immediately so no speech is lost during model init
            val ar = createAndStartAudioRecord()
            isListening = true
            _transcriptionFlow.emit(TranscriptionResult.InProgress)

            // Buffer audio samples while models load
            val preBuffer = ArrayDeque<FloatArray>()
            recognitionJob =
                scope.launch(Dispatchers.IO) {
                    val shortBuffer = ShortArray(BUFFER_SIZE_SHORTS)

                    // Phase 1: buffer audio while models initialize
                    val initJob =
                        launch {
                            initializeModels()
                        }

                    while (isActive && isListening && initJob.isActive) {
                        val shortsRead = ar.read(shortBuffer, 0, shortBuffer.size)
                        if (shortsRead > 0) {
                            preBuffer.addLast(shortsToFloats(shortBuffer, shortsRead))
                        }
                    }

                    if (!isActive || !isListening) return@launch

                    // Phase 2: models ready — create stream and drain buffer
                    val rec = recognizer ?: return@launch
                    val s = rec.createStream()
                    stream = s

                    for (samples in preBuffer) {
                        s.acceptWaveform(samples, SAMPLE_RATE)
                        while (rec.isReady(s)) {
                            rec.decode(s)
                        }
                        processEndpointResults(rec, s)
                    }
                    preBuffer.clear()

                    // Phase 3: live decode loop
                    while (isActive && isListening) {
                        val shortsRead = ar.read(shortBuffer, 0, shortBuffer.size)
                        if (shortsRead <= 0) continue

                        s.acceptWaveform(shortsToFloats(shortBuffer, shortsRead), SAMPLE_RATE)

                        while (rec.isReady(s)) {
                            rec.decode(s)
                        }
                        processEndpointResults(rec, s)
                    }
                }

            Napier.d("Sherpa-ONNX recognition started (${SAMPLE_RATE}Hz, mono, PCM 16-bit)")
            true
        } catch (e: Exception) {
            Napier.e("Failed to start Sherpa-ONNX transcription", e)
            _transcriptionFlow.emit(TranscriptionResult.Error("Failed to start transcription: ${e.message}", e))
            false
        }
    }

    override suspend fun stopLiveTranscription() {
        if (!isListening) return
        isListening = false

        // Stop audio first so the recognition loop exits naturally
        stopAudioRecord()

        // Wait for the recognition coroutine to finish before touching the stream
        recognitionJob?.join()
        recognitionJob = null

        // Now safe to get final result from the stream
        try {
            val rec = recognizer
            val s = stream
            if (rec != null && s != null) {
                while (rec.isReady(s)) {
                    rec.decode(s)
                }
                val result = rec.getResult(s)
                if (result.text.isNotBlank()) {
                    val punctuated = addPunctuation(result.text)
                    accumulatedSegments.add(punctuated)
                    _transcriptionFlow.emit(TranscriptionResult.Success(buildAccumulatedText()))
                }
            }
        } catch (e: Exception) {
            Napier.e("Error getting final transcription result", e)
        }

        releaseStream()
    }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult =
        TranscriptionResult.Error("File transcription not yet supported with Sherpa-ONNX")

    override fun cancelTranscription() {
        isListening = false
        recognitionJob?.cancel()
        recognitionJob = null
        stopAudioRecord()
        releaseStream()
    }

    override fun getSupportedLanguages(): List<String> = listOf("en-US")

    override fun setLanguage(languageCode: String) {
        Napier.d("Sherpa-ONNX language set request: $languageCode (only en-US supported)")
    }

    override val supportsLiveTranscription: Boolean = true

    override val supportsFileTranscription: Boolean = false

    override suspend fun resetTranscription() {
        accumulatedSegments.clear()
        currentPartial = ""

        if (isListening) {
            stopLiveTranscription()
            _transcriptionFlow.emit(TranscriptionResult.InProgress)
            startLiveTranscription()
        }
    }

    override fun release() {
        isListening = false
        recognitionJob?.cancel()
        recognitionJob = null
        stopAudioRecord()
        releaseStream()
        recognizer?.release()
        recognizer = null
        punctuation?.release()
        punctuation = null
        accumulatedSegments.clear()
        currentPartial = ""
    }

    private suspend fun initializeModels() =
        modelInitMutex.withLock {
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

    private fun createAndStartAudioRecord(): AudioRecord {
        val bufferSize =
            AudioRecord
                .getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(BUFFER_SIZE_BYTES)

        val ar =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        ar.startRecording()
        audioRecord = ar
        return ar
    }

    private suspend fun processEndpointResults(
        rec: OnlineRecognizer,
        s: OnlineStream,
    ) {
        val result = rec.getResult(s)

        if (rec.isEndpoint(s)) {
            if (result.text.isNotBlank()) {
                val punctuated = addPunctuation(result.text)
                accumulatedSegments.add(punctuated)
                currentPartial = ""
                _transcriptionFlow.emit(TranscriptionResult.Success(buildAccumulatedText()))
            }
            rec.reset(s)
        } else if (result.text.isNotBlank()) {
            currentPartial = result.text.lowercase()
            _transcriptionFlow.emit(TranscriptionResult.Success(buildAccumulatedText()))
        }
    }

    private fun shortsToFloats(
        shorts: ShortArray,
        count: Int,
    ): FloatArray = FloatArray(count) { shorts[it] / 32768.0f }

    private fun addPunctuation(text: String): String =
        try {
            // STT model outputs uppercase tokens; lowercase before punctuation
            // since the punctuation model's BPE vocabulary is lowercase-based.
            // The punctuation model restores proper capitalization.
            val lowered = text.lowercase()
            punctuation?.addPunctuation(lowered) ?: lowered
        } catch (e: Exception) {
            Napier.e("Punctuation model error, returning raw text", e)
            text.lowercase()
        }

    private fun buildAccumulatedText(): String {
        val base = accumulatedSegments.joinToString(" ")
        return if (currentPartial.isNotBlank()) {
            if (base.isNotBlank()) "$base $currentPartial" else currentPartial
        } else {
            base
        }
    }

    private fun stopAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Napier.e("Error stopping AudioRecord", e)
        }
        audioRecord = null
    }

    private fun releaseStream() {
        try {
            stream?.release()
        } catch (e: Exception) {
            Napier.e("Error releasing Sherpa-ONNX stream", e)
        }
        stream = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE_SHORTS = 2048
        private const val BUFFER_SIZE_BYTES = BUFFER_SIZE_SHORTS * 2
    }
}
