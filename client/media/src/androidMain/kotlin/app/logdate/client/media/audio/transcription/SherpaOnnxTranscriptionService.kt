package app.logdate.client.media.audio.transcription

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OnlineStream
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * On-device transcription service using Sherpa-ONNX speech recognition with
 * online punctuation.
 *
 * Uses [AudioRecord] (not [MediaRecorder]) to capture raw PCM audio, which does NOT
 * request audio focus — music playback continues uninterrupted. The PCM stream is fed
 * to a Sherpa-ONNX recognizer (via [SherpaOnnxRecognizerProvider]) for streaming
 * speech-to-text.
 *
 * Finalized segments are run through the punctuation model to add
 * capitalization and punctuation before being appended to accumulated text.
 */
class SherpaOnnxTranscriptionService(
    private val context: Context,
    private val recognizerProvider: SherpaOnnxRecognizerProvider,
    private val scope: CoroutineScope,
    private val accumulator: TranscriptAccumulator,
) : TranscriptionService {
    private val _transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)

    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var recognitionJob: Job? = null

    @Volatile
    private var isListening = false

    override suspend fun warmUp() {
        recognizerProvider.ensureInitialized()
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
                            recognizerProvider.ensureInitialized()
                        }

                    while (isActive && isListening && initJob.isActive) {
                        val shortsRead = ar.read(shortBuffer, 0, shortBuffer.size)
                        if (shortsRead > 0) {
                            preBuffer.addLast(shortsToFloats(shortBuffer, shortsRead))
                        }
                    }

                    if (!isActive || !isListening) return@launch

                    // Phase 2: models ready — create stream and drain buffer
                    val s = recognizerProvider.createStream()
                    stream = s

                    for (samples in preBuffer) {
                        s.acceptWaveform(samples, SherpaOnnxRecognizerProvider.SAMPLE_RATE)
                        while (recognizerProvider.isReady(s)) {
                            recognizerProvider.decode(s)
                        }
                        processEndpointResults(s)
                    }
                    preBuffer.clear()

                    // Phase 3: live decode loop
                    while (isActive && isListening) {
                        val shortsRead = ar.read(shortBuffer, 0, shortBuffer.size)
                        if (shortsRead <= 0) continue

                        s.acceptWaveform(shortsToFloats(shortBuffer, shortsRead), SherpaOnnxRecognizerProvider.SAMPLE_RATE)

                        while (recognizerProvider.isReady(s)) {
                            recognizerProvider.decode(s)
                        }
                        processEndpointResults(s)
                    }
                }

            Napier.d("Sherpa-ONNX recognition started (${SherpaOnnxRecognizerProvider.SAMPLE_RATE}Hz, mono, PCM 16-bit)")
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
            val s = stream
            if (s != null) {
                while (recognizerProvider.isReady(s)) {
                    recognizerProvider.decode(s)
                }
                val result = recognizerProvider.getResult(s)
                if (result.text.isNotBlank()) {
                    val punctuated = recognizerProvider.addPunctuation(result.text)
                    accumulator.addSegment(punctuated)
                    _transcriptionFlow.emit(TranscriptionResult.Success(accumulator.build()))
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
        accumulator.reset()

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
        accumulator.reset()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun createAndStartAudioRecord(): AudioRecord {
        val bufferSize =
            AudioRecord
                .getMinBufferSize(
                    SherpaOnnxRecognizerProvider.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(BUFFER_SIZE_BYTES)

        val ar =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SherpaOnnxRecognizerProvider.SAMPLE_RATE,
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

    private suspend fun processEndpointResults(s: OnlineStream) {
        val result = recognizerProvider.getResult(s)

        if (recognizerProvider.isEndpoint(s)) {
            if (result.text.isNotBlank()) {
                val punctuated = recognizerProvider.addPunctuation(result.text)
                accumulator.addSegment(punctuated)
                _transcriptionFlow.emit(TranscriptionResult.Success(accumulator.build()))
            }
            recognizerProvider.reset(s)
        } else if (result.text.isNotBlank()) {
            accumulator.setPartial(result.text.lowercase())
            _transcriptionFlow.emit(TranscriptionResult.Success(accumulator.build()))
        }
    }

    private fun shortsToFloats(
        shorts: ShortArray,
        count: Int,
    ): FloatArray = FloatArray(count) { shorts[it] / 32768.0f }

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
        private const val BUFFER_SIZE_SHORTS = 2048
        private const val BUFFER_SIZE_BYTES = BUFFER_SIZE_SHORTS * 2
    }
}
