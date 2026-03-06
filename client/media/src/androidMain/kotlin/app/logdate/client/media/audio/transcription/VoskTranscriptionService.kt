package app.logdate.client.media.audio.transcription

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * On-device transcription service using Vosk speech recognition.
 *
 * Uses [AudioRecord] (not [MediaRecorder]) to capture raw PCM audio, which does NOT
 * request audio focus — music playback continues uninterrupted. The PCM stream is fed
 * to a Vosk [Recognizer] for streaming speech-to-text.
 *
 * Runs in parallel with [MediaRecorder] in [AudioRecordingService][app.logdate.client.media.audio.AudioRecordingService],
 * which writes the M4A file. Both can use the microphone simultaneously on API 30+.
 */
class VoskTranscriptionService(
    private val context: Context,
) : TranscriptionService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)

    private val modelManager = VoskModelManager(context)
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recognitionJob: Job? = null

    private val accumulatedSegments = mutableListOf<String>()
    private var currentPartial: String = ""
    private var isListening = false

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = _transcriptionFlow.asSharedFlow()

    override suspend fun startLiveTranscription(): Boolean {
        if (isListening) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Napier.e("RECORD_AUDIO permission not granted for Vosk transcription")
            _transcriptionFlow.emit(TranscriptionResult.Error("Microphone permission not granted"))
            return false
        }

        return try {
            initializeModel()
            startRecognition()
            true
        } catch (e: Exception) {
            Napier.e("Failed to start Vosk transcription", e)
            _transcriptionFlow.emit(TranscriptionResult.Error("Failed to start transcription: ${e.message}", e))
            false
        }
    }

    override suspend fun stopLiveTranscription() {
        isListening = false
        recognitionJob?.cancel()
        recognitionJob = null

        // Finalize any remaining audio
        recognizer?.let { rec ->
            val finalResult = rec.finalResult
            parseFinalResult(finalResult)
        }

        stopAudioRecord()
        closeRecognizer()
    }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult =
        TranscriptionResult.Error("File transcription not yet supported with Vosk")

    override fun cancelTranscription() {
        isListening = false
        recognitionJob?.cancel()
        recognitionJob = null
        stopAudioRecord()
        closeRecognizer()
    }

    override fun getSupportedLanguages(): List<String> = listOf("en-US")

    override fun setLanguage(languageCode: String) {
        // Currently only English model is bundled
        Napier.d("Vosk language set request: $languageCode (only en-US supported)")
    }

    override val supportsLiveTranscription: Boolean = true

    override val supportsFileTranscription: Boolean = false

    override suspend fun resetTranscription() {
        accumulatedSegments.clear()
        currentPartial = ""

        if (isListening) {
            // Stop and restart recognition for a clean slate
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
        closeRecognizer()
        model?.close()
        model = null
        accumulatedSegments.clear()
        currentPartial = ""
    }

    private suspend fun initializeModel() {
        if (model != null) return

        val modelPath =
            withContext(Dispatchers.IO) {
                modelManager.getModelPath()
            } ?: throw IllegalStateException("Vosk model not available")

        model =
            withContext(Dispatchers.IO) {
                Model(modelPath)
            }
        Napier.d("Vosk model loaded from $modelPath")
    }

    private fun startRecognition() {
        val voskModel = model ?: throw IllegalStateException("Model not initialized")

        recognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat())

        val bufferSize =
            AudioRecord
                .getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(BUFFER_SIZE)

        audioRecord =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        audioRecord?.startRecording()
        isListening = true

        scope.launch {
            _transcriptionFlow.emit(TranscriptionResult.InProgress)
        }

        recognitionJob =
            scope.launch {
                val buffer = ByteArray(BUFFER_SIZE)
                val rec = recognizer ?: return@launch

                while (isActive && isListening) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead <= 0) continue

                    val isFinal = rec.acceptWaveForm(buffer, bytesRead)
                    if (isFinal) {
                        parseFinalResult(rec.result)
                    } else {
                        parsePartialResult(rec.partialResult)
                    }
                }
            }

        Napier.d("Vosk recognition started (${SAMPLE_RATE}Hz, mono, PCM 16-bit)")
    }

    private fun parseFinalResult(json: String) {
        try {
            val text = JSONObject(json).optString("text", "").trim()
            if (text.isNotBlank()) {
                accumulatedSegments.add(text)
                currentPartial = ""
                scope.launch {
                    _transcriptionFlow.emit(TranscriptionResult.Success(buildAccumulatedText()))
                }
            }
        } catch (e: Exception) {
            Napier.e("Error parsing Vosk final result", e)
        }
    }

    private fun parsePartialResult(json: String) {
        try {
            val partial = JSONObject(json).optString("partial", "").trim()
            if (partial.isNotBlank()) {
                currentPartial = partial
                scope.launch {
                    _transcriptionFlow.emit(TranscriptionResult.Success(buildAccumulatedText()))
                }
            }
        } catch (e: Exception) {
            Napier.e("Error parsing Vosk partial result", e)
        }
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

    private fun closeRecognizer() {
        try {
            recognizer?.close()
        } catch (e: Exception) {
            Napier.e("Error closing Vosk recognizer", e)
        }
        recognizer = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = 4096
    }
}
