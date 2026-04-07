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
import kotlinx.coroutines.currentCoroutineContext
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
    private val vadProvider: SherpaOnnxVadProvider,
    private val offlineRecognizerProvider: SherpaOnnxOfflineRecognizerProvider,
    private val scope: CoroutineScope,
    private val accumulator: TranscriptAccumulator,
) : TranscriptionService {
    private val _transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)

    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var recognitionJob: Job? = null
    private var refinementJob: Job? = null
    private var totalAcceptedSamples: Long = 0L
    private var currentStreamStartMs: Long = 0L
    private var currentStreamAcceptedSamples: Long = 0L

    @Volatile
    private var isListening = false

    private val floatBuffer = FloatArray(BUFFER_SIZE_SHORTS)

    /**
     * Per-utterance PCM buffers captured during the live pass and consumed by
     * the Whisper refinement pass after recording stops. Each entry corresponds
     * to one VAD-detected speech segment, giving Whisper a clean utterance to
     * decode without trailing silence.
     *
     * Capped at [MAX_BUFFERED_SAMPLES] (~15 minutes of speech). If exceeded,
     * the buffer is dropped and refinement is skipped — the streaming text
     * stands as final.
     */
    private var utterancePcmBuffer: ArrayList<FloatArray> = ArrayList()
    private var bufferedSampleCount: Long = 0
    private var bufferOverflowed = false

    override suspend fun warmUp() {
        recognizerProvider.ensureInitialized()
        vadProvider.ensureInitialized()
        // Refinement is optional. If the Whisper model hasn't been downloaded
        // yet, ensureInitialized() returns false instead of throwing — the app
        // falls back to streaming-only transcription without the user noticing.
        offlineRecognizerProvider.ensureInitialized()
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

        clearRefinementBuffer()
        // The user is starting a new session — any in-flight refinement from
        // the previous one is no longer relevant.
        refinementJob?.cancel()
        refinementJob = null

        return try {
            // Start capturing audio immediately so no speech is lost during model init
            val ar = createAndStartAudioRecord()
            isListening = true
            _transcriptionFlow.emit(TranscriptionResult.InProgress)

            // Pre-warm Whisper while the user records. Runs on Default (CPU-bound
            // model load), not IO, so it doesn't serialize behind the audio capture
            // loop which also lives on Dispatchers.IO.
            scope.launch(Dispatchers.Default) {
                try {
                    offlineRecognizerProvider.ensureInitialized()
                } catch (e: Exception) {
                    Napier.w("Whisper pre-warm failed; refinement will be unavailable", e)
                }
            }

            // Buffer audio samples while models load
            val preBuffer = ArrayDeque<FloatArray>()
            recognitionJob =
                scope.launch(Dispatchers.IO) {
                    val shortBuffer = ShortArray(BUFFER_SIZE_SHORTS)

                    // Phase 1: buffer audio while models initialize
                    val initJob =
                        launch {
                            recognizerProvider.ensureInitialized()
                            vadProvider.ensureInitialized()
                        }

                    while (isActive && isListening && initJob.isActive) {
                        val shortsRead = ar.read(shortBuffer, 0, shortBuffer.size)
                        if (shortsRead > 0) {
                            preBuffer.addLast(shortsToFloats(shortBuffer, shortsRead))
                        }
                    }

                    if (!isActive || !isListening) return@launch

                    // Phase 2: models ready — create stream and drain buffer through VAD
                    val s = recognizerProvider.createStream()
                    stream = s

                    for (samples in preBuffer) {
                        processSamples(s, samples)
                    }
                    preBuffer.clear()

                    // Phase 3: live decode loop
                    while (isActive && isListening) {
                        val shortsRead = ar.read(shortBuffer, 0, shortBuffer.size)
                        if (shortsRead <= 0) continue

                        processSamples(s, shortsToFloats(shortBuffer, shortsRead))
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

        // Drain any speech the VAD was still mid-window on when audio capture
        // stopped — without flush() these trailing samples would be silently
        // dropped and the user's last few words would never reach the recognizer
        // (or the refinement buffer).
        try {
            val s = stream
            if (s != null) {
                vadProvider.flush()
                while (!vadProvider.isEmpty()) {
                    val segment = vadProvider.front()
                    vadProvider.pop()
                    bufferUtteranceForRefinement(segment.samples)
                    acceptWaveform(s, segment.samples)
                }
            }
        } catch (e: Exception) {
            Napier.e("Error flushing VAD on stop", e)
        }

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
                }
            }
        } catch (e: Exception) {
            Napier.e("Error getting final transcription result", e)
        }

        // Decide whether to refine. If Whisper is loaded and the buffer fits,
        // emit the streaming result with isRefining=true and start the
        // background rewrite. Otherwise emit a final-only Success.
        val canRefine = !bufferOverflowed && utterancePcmBuffer.isNotEmpty() && offlineRecognizerProvider.isAvailable
        _transcriptionFlow.emit(
            TranscriptionResult.Success(
                text = accumulator.build(),
                timedTranscript = accumulator.buildTimedTranscript(),
                isFinal = true,
                isRefining = canRefine,
            ),
        )

        currentStreamStartMs = samplesToMs(totalAcceptedSamples)
        currentStreamAcceptedSamples = 0L

        vadProvider.reset()
        releaseStream()

        if (canRefine) {
            // Hand the buffer off to the refinement pass by swapping in a fresh
            // ArrayList. The refinement coroutine owns the old reference exclusively
            // — no copy, no doubled peak memory under the cap.
            val utterances = utterancePcmBuffer
            utterancePcmBuffer = ArrayList()
            bufferedSampleCount = 0
            refinementJob =
                scope.launch(Dispatchers.Default) {
                    runRefinement(utterances)
                }
        }
    }

    /**
     * The refinement pass. Walks the buffered VAD utterances in order, sending
     * each one through Whisper and replacing the corresponding portion of the
     * accumulator with the refined text. After every utterance, emits an
     * updated [TranscriptionResult.Success] so the UI can crossfade the change
     * in place — the user sees the transcript visibly correcting itself.
     */
    private suspend fun runRefinement(utterances: List<FloatArray>) {
        try {
            // Make sure Whisper is actually loaded before we touch it
            if (!offlineRecognizerProvider.ensureInitialized()) {
                Napier.w("Whisper not available for refinement; keeping streaming text")
                return
            }

            // Reset the accumulator so we can rebuild it utterance-by-utterance
            // with refined text. We do this AFTER the streaming Success was
            // emitted above, so the UI keeps showing the streaming text until
            // the first refined chunk arrives.
            val refinedAccumulator = TranscriptAccumulator()

            for ((index, samples) in utterances.withIndex()) {
                if (!currentCoroutineContext().isActive) return

                val result = offlineRecognizerProvider.transcribe(samples) ?: continue
                if (result.text.isBlank()) continue

                refinedAccumulator.addSegment(result.text)

                val isLast = index == utterances.lastIndex
                _transcriptionFlow.emit(
                    TranscriptionResult.Success(
                        text = refinedAccumulator.build(),
                        timedTranscript = refinedAccumulator.buildTimedTranscript(),
                        isFinal = true,
                        isRefining = !isLast,
                    ),
                )
            }
        } catch (e: Exception) {
            Napier.e("Refinement pass failed; keeping streaming text", e)
        }
    }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult =
        TranscriptionResult.Error("File transcription not yet supported with Sherpa-ONNX")

    override fun cancelTranscription() {
        isListening = false
        cancelJobs()
        clearRefinementBuffer()
        stopAudioRecord()
        vadProvider.reset()
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
        cancelJobs()
        clearRefinementBuffer()
        stopAudioRecord()
        releaseStream()
        vadProvider.release()
        offlineRecognizerProvider.release()
        accumulator.reset()
        totalAcceptedSamples = 0L
        currentStreamStartMs = 0L
        currentStreamAcceptedSamples = 0L
    }

    private fun cancelJobs() {
        recognitionJob?.cancel()
        recognitionJob = null
        refinementJob?.cancel()
        refinementJob = null
    }

    private fun clearRefinementBuffer() {
        utterancePcmBuffer.clear()
        bufferedSampleCount = 0
        bufferOverflowed = false
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

    /**
     * Routes raw PCM samples through the VAD, then forwards detected speech
     * segments to the recognizer. Silence is dropped before reaching the
     * recognizer, eliminating hallucinated tokens during pauses.
     */
    private suspend fun processSamples(
        s: OnlineStream,
        samples: FloatArray,
    ) {
        vadProvider.acceptWaveform(samples)
        while (!vadProvider.isEmpty()) {
            val segment = vadProvider.front()
            vadProvider.pop()
            bufferUtteranceForRefinement(segment.samples)
            acceptWaveform(s, segment.samples)
            while (recognizerProvider.isReady(s)) {
                recognizerProvider.decode(s)
            }
            processEndpointResults(s)
        }
    }

    /**
     * Captures a VAD utterance into the in-memory buffer that the Whisper
     * refinement pass will consume. Each entry is one speech segment, so
     * Whisper sees clean utterances without trailing silence padding.
     *
     * Drops the buffer entirely if total buffered audio exceeds
     * [MAX_BUFFERED_SAMPLES] — refinement is skipped for very long recordings
     * to keep memory bounded. The streaming text remains the final result.
     */
    private fun bufferUtteranceForRefinement(samples: FloatArray) {
        if (bufferOverflowed || samples.isEmpty()) return
        if (bufferedSampleCount + samples.size > MAX_BUFFERED_SAMPLES) {
            Napier.w("Refinement buffer exceeded ${MAX_BUFFERED_SAMPLES / SherpaOnnxRecognizerProvider.SAMPLE_RATE}s; dropping")
            utterancePcmBuffer.clear()
            bufferedSampleCount = 0
            bufferOverflowed = true
            return
        }
        // Defensive copy: the FloatArray returned by SpeechSegment is owned by
        // the VAD/native side and may be reused. We need our own copy to keep
        // around for the refinement pass.
        utterancePcmBuffer += samples.copyOf()
        bufferedSampleCount += samples.size
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

        /**
         * Maximum samples retained in memory for the refinement pass.
         * 15 minutes at 16kHz mono = ~57 MB of float data. Beyond this, the
         * buffer is dropped and the streaming text becomes the final result.
         */
        private const val MAX_BUFFERED_SAMPLES = 15L * 60 * SherpaOnnxRecognizerProvider.SAMPLE_RATE
    }
}
