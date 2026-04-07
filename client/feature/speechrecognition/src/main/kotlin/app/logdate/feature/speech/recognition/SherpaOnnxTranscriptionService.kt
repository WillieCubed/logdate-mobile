package app.logdate.feature.speech.recognition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import app.logdate.client.media.audio.transcription.TimedTranscriptBuilder
import app.logdate.client.media.audio.transcription.TimedUtterance
import app.logdate.client.media.audio.transcription.TranscriptAccumulator
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import com.k2fsa.sherpa.onnx.OnlineRecognizerResult
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
    private var totalAcceptedSamples: Long = 0L
    private var currentStreamStartMs: Long = 0L
    private var currentStreamAcceptedSamples: Long = 0L

    @Volatile
    private var isListening = false

    private val floatBuffer = FloatArray(BUFFER_SIZE_SHORTS)

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
                        acceptWaveform(s, samples)
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

                        acceptWaveform(s, shortsToFloats(shortBuffer, shortsRead))

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
                    val utterance = buildTimedUtterance(result, punctuated)
                    accumulator.addSegment(punctuated, utterance)
                    _transcriptionFlow.emit(
                        TranscriptionResult.Success(
                            text = accumulator.build(),
                            timedTranscript = accumulator.buildTimedTranscript(),
                            isFinal = true,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Napier.e("Error getting final transcription result", e)
        }

        currentStreamStartMs = samplesToMs(totalAcceptedSamples)
        currentStreamAcceptedSamples = 0L

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
        totalAcceptedSamples = 0L
        currentStreamStartMs = 0L
        currentStreamAcceptedSamples = 0L

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
        totalAcceptedSamples = 0L
        currentStreamStartMs = 0L
        currentStreamAcceptedSamples = 0L
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
                val utterance = buildTimedUtterance(result, punctuated)
                accumulator.addSegment(punctuated, utterance)
                _transcriptionFlow.emit(
                    TranscriptionResult.Success(
                        text = accumulator.build(),
                        timedTranscript = accumulator.buildTimedTranscript(),
                        isFinal = true,
                    ),
                )
            }
            currentStreamStartMs = samplesToMs(totalAcceptedSamples)
            currentStreamAcceptedSamples = 0L
            recognizerProvider.reset(s)
        } else if (result.text.isNotBlank()) {
            accumulator.setPartial(result.text)
            _transcriptionFlow.emit(
                TranscriptionResult.Success(
                    text = accumulator.build(),
                    timedTranscript = accumulator.buildTimedTranscript(),
                    isFinal = false,
                ),
            )
        }
    }

    private fun acceptWaveform(
        stream: OnlineStream,
        samples: FloatArray,
    ) {
        if (samples.isEmpty()) return
        stream.acceptWaveform(samples, SherpaOnnxRecognizerProvider.SAMPLE_RATE)
        totalAcceptedSamples += samples.size.toLong()
        currentStreamAcceptedSamples += samples.size.toLong()
    }

    private fun buildTimedUtterance(
        result: OnlineRecognizerResult,
        punctuatedText: String,
    ): TimedUtterance? =
        TimedTranscriptBuilder.buildUtterance(
            text = punctuatedText,
            utteranceStartMs = currentStreamStartMs,
            utteranceConsumedMs = samplesToMs(currentStreamAcceptedSamples),
            tokens = result.tokens.toList(),
            timestampsSeconds = result.timestamps.toList(),
        )

    private fun samplesToMs(sampleCount: Long): Long =
        ((sampleCount * 1000L) / SherpaOnnxRecognizerProvider.SAMPLE_RATE).coerceAtLeast(0L)

    private fun shortsToFloats(
        shorts: ShortArray,
        count: Int,
    ): FloatArray {
        for (i in 0 until count) floatBuffer[i] = shorts[i] / 32768.0f
        return floatBuffer.copyOf(count)
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
        private const val BUFFER_SIZE_SHORTS = 2048
        private const val BUFFER_SIZE_BYTES = BUFFER_SIZE_SHORTS * 2
    }
}
