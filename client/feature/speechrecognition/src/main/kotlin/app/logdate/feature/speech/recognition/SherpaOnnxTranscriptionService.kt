package app.logdate.feature.speech.recognition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import app.logdate.client.media.audio.download.ModelDownloadStatus
import app.logdate.client.media.audio.transcription.TimedTranscriptBuilder
import app.logdate.client.media.audio.transcription.TimedUtterance
import app.logdate.client.media.audio.transcription.TranscriptAccumulator
import app.logdate.client.media.audio.transcription.TranscriptionFailure
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionSessionTerminalizer
import app.logdate.client.media.audio.transcription.TranscriptionStartResult
import com.k2fsa.sherpa.onnx.OnlineRecognizerResult
import com.k2fsa.sherpa.onnx.OnlineStream
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    private val terminalizer = TranscriptionSessionTerminalizer(_transcriptionFlow::emit)

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

    override suspend fun startLiveTranscription(): TranscriptionStartResult {
        if (isListening) return TranscriptionStartResult.AlreadyRunning

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Napier.e("RECORD_AUDIO permission not granted for transcription")
            _transcriptionFlow.emit(TranscriptionResult.Error(TranscriptionFailure.PermissionDenied))
            return TranscriptionStartResult.Failed(TranscriptionFailure.PermissionDenied)
        }

        // The user is starting a new session — any in-flight refinement from
        // the previous one is no longer relevant.
        refinementJob?.cancelAndJoin()
        refinementJob = null
        terminalizer.cancel()
        clearRefinementBuffer()

        var sessionAccepted = false
        return try {
            // Start capturing audio immediately so no speech is lost during model init
            val ar = createAndStartAudioRecord()
            val acknowledgement = terminalizer.begin()
            if (acknowledgement != TranscriptionStartResult.Started) {
                stopAudioRecord()
                return acknowledgement
            }
            sessionAccepted = true
            isListening = true

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
                    try {
                        val shortBuffer = ShortArray(BUFFER_SIZE_SHORTS)
                        var consecutiveEmptyReads = 0

                        suspend fun readSamples(): Int {
                            val count = ar.read(shortBuffer, 0, shortBuffer.size)
                            when {
                                count > 0 -> consecutiveEmptyReads = 0
                                count < 0 -> throw AudioCaptureException("AudioRecord.read failed with code $count")
                                else -> {
                                    consecutiveEmptyReads += 1
                                    if (consecutiveEmptyReads >= MAX_CONSECUTIVE_EMPTY_READS) {
                                        throw AudioCaptureException("AudioRecord produced no samples")
                                    }
                                    delay(EMPTY_READ_RETRY_DELAY_MS)
                                }
                            }
                            return count
                        }

                        // Phase 1: buffer audio while models initialize. A
                        // supervisor keeps initialization failure observable via
                        // await() instead of cancelling this parent silently.
                        withTimeout(MODEL_INITIALIZATION_TIMEOUT_MS) {
                            supervisorScope {
                                val initialization =
                                    async {
                                        recognizerProvider.ensureInitialized()
                                        vadProvider.ensureInitialized()
                                    }

                                while (isActive && isListening && initialization.isActive) {
                                    val shortsRead = readSamples()
                                    if (shortsRead > 0) {
                                        preBuffer.addLast(shortsToFloats(shortBuffer, shortsRead))
                                    }
                                }
                                initialization.await()
                            }
                        }

                        currentCoroutineContext().ensureActive()

                        // Phase 2: models ready — create stream and drain buffer through VAD
                        val s = recognizerProvider.createStream()
                        stream = s

                        for (samples in preBuffer) {
                            processSamples(s, samples)
                        }
                        preBuffer.clear()

                        // Phase 3: live decode loop
                        while (isActive && isListening) {
                            val shortsRead = readSamples()
                            if (shortsRead <= 0) continue

                            processSamples(s, shortsToFloats(shortBuffer, shortsRead))
                        }
                    } catch (e: TimeoutCancellationException) {
                        isListening = false
                        stopAudioRecord()
                        Napier.e("Live transcription model initialization timed out", e)
                        terminalizer.fail(TranscriptionFailure.NotAvailable)
                        vadProvider.reset()
                        releaseStream()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (!isListening && e is AudioCaptureException) return@launch
                        isListening = false
                        stopAudioRecord()
                        val reason =
                            if (e is AudioCaptureException) {
                                TranscriptionFailure.AudioError
                            } else {
                                TranscriptionFailure.NotAvailable
                            }
                        Napier.e("Live transcription failed after start", e)
                        terminalizer.fail(reason)
                        vadProvider.reset()
                        releaseStream()
                    }
                }

            Napier.d("Sherpa-ONNX recognition started (${SherpaOnnxRecognizerProvider.SAMPLE_RATE}Hz, mono, PCM 16-bit)")
            TranscriptionStartResult.Started
        } catch (e: Exception) {
            isListening = false
            stopAudioRecord()
            Napier.e("Failed to start Sherpa-ONNX transcription", e)
            val reason = TranscriptionFailure.AudioError
            if (sessionAccepted) {
                terminalizer.fail(reason)
            } else {
                _transcriptionFlow.emit(TranscriptionResult.Error(reason))
            }
            TranscriptionStartResult.Failed(reason)
        }
    }

    override suspend fun stopLiveTranscription(): TranscriptionResult {
        if (!isListening) {
            return _transcriptionFlow.replayCache.lastOrNull() ?: TranscriptionResult.Cancelled
        }
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
            terminalizer.fail(TranscriptionFailure.AudioError)
            vadProvider.reset()
            releaseStream()
            return TranscriptionResult.Error(TranscriptionFailure.AudioError)
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
            terminalizer.fail(TranscriptionFailure.Unknown)
            vadProvider.reset()
            releaseStream()
            return TranscriptionResult.Error(TranscriptionFailure.Unknown)
        }

        // Decide whether to refine. If Whisper is loaded and the buffer fits,
        // emit the streaming result with isRefining=true and start the
        // background rewrite. Otherwise emit a final-only Success.
        val canRefine = !bufferOverflowed && utterancePcmBuffer.isNotEmpty() && offlineRecognizerProvider.isAvailable
        val streamingText = accumulator.build()
        val streamingResult =
            TranscriptionResult.Success(
                text = streamingText,
                timedTranscript = accumulator.buildTimedTranscript(),
                isFinal = true,
                isRefining = canRefine,
            )
        val stopResult =
            if (streamingText.isBlank()) {
                TranscriptionResult.Error(TranscriptionFailure.NoSpeechDetected)
            } else {
                streamingResult
            }
        if (streamingText.isBlank()) {
            terminalizer.fail(TranscriptionFailure.NoSpeechDetected)
        } else if (canRefine) {
            terminalizer.progress(streamingResult)
        } else {
            terminalizer.complete(streamingResult)
        }

        currentStreamStartMs = samplesToMs(totalAcceptedSamples)
        currentStreamAcceptedSamples = 0L

        vadProvider.reset()
        releaseStream()

        if (canRefine && streamingText.isNotBlank()) {
            // Hand the buffer off to the refinement pass by swapping in a fresh
            // ArrayList. The refinement coroutine owns the old reference exclusively
            // — no copy, no doubled peak memory under the cap.
            val utterances = utterancePcmBuffer
            utterancePcmBuffer = ArrayList()
            bufferedSampleCount = 0
            refinementJob =
                scope.launch(Dispatchers.Default) {
                    runRefinement(
                        utterances = utterances,
                        streamingFallback = streamingResult.copy(isRefining = false),
                    )
                }
        }
        return stopResult
    }

    /**
     * The refinement pass. Walks the buffered VAD utterances in order, sending
     * each one through Whisper and replacing the corresponding portion of the
     * accumulator with the refined text. After every utterance, emits an
     * updated [TranscriptionResult.Success] so the UI can crossfade the change
     * in place — the user sees the transcript visibly correcting itself.
     */
    private suspend fun runRefinement(
        utterances: List<FloatArray>,
        streamingFallback: TranscriptionResult.Success,
    ) {
        try {
            withTimeout(REFINEMENT_TIMEOUT_MS) {
                // Make sure Whisper is actually loaded before we touch it
                if (!offlineRecognizerProvider.ensureInitialized()) {
                    Napier.w("Whisper not available for refinement; keeping streaming text")
                    terminalizer.complete(streamingFallback)
                    return@withTimeout
                }

                // Reset the accumulator so we can rebuild it utterance-by-utterance
                // with refined text. We do this AFTER the streaming Success was
                // emitted above, so the UI keeps showing the streaming text until
                // the first refined chunk arrives.
                val refinedAccumulator = TranscriptAccumulator()

                for (samples in utterances) {
                    currentCoroutineContext().ensureActive()

                    val result = offlineRecognizerProvider.transcribe(samples) ?: continue
                    if (result.text.isBlank()) continue

                    refinedAccumulator.addSegment(result.text)

                    terminalizer.progress(
                        TranscriptionResult.Success(
                            text = refinedAccumulator.build(),
                            timedTranscript = refinedAccumulator.buildTimedTranscript(),
                            isFinal = true,
                            isRefining = true,
                        ),
                    )
                }
                val refinedText = refinedAccumulator.build()
                terminalizer.complete(
                    if (refinedText.isBlank()) {
                        streamingFallback
                    } else {
                        TranscriptionResult.Success(
                            text = refinedText,
                            timedTranscript = refinedAccumulator.buildTimedTranscript(),
                            isFinal = true,
                            isRefining = false,
                        )
                    },
                )
            }
        } catch (e: TimeoutCancellationException) {
            Napier.e("Refinement timed out; keeping streaming text", e)
            terminalizer.complete(streamingFallback)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Refinement pass failed; keeping streaming text", e)
            terminalizer.complete(streamingFallback)
        }
    }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult =
        withContext(Dispatchers.Default) {
            val samples = AudioDecoder(context).decodeToMono16kHz(audioUri)
                ?: return@withContext TranscriptionResult.Error(TranscriptionFailure.AudioError)
            if (samples.isEmpty()) {
                return@withContext TranscriptionResult.Error(TranscriptionFailure.NoSpeechDetected)
            }

            val fileVad = SherpaOnnxVadProvider(context)
            try {
                fileVad.ensureInitialized()
                samples.asSequenceChunks(BUFFER_SIZE_SHORTS).forEach(fileVad::acceptWaveform)
                fileVad.flush()
                val utterances = buildList {
                    while (!fileVad.isEmpty()) {
                        add(fileVad.front().samples.copyOf())
                        fileVad.pop()
                    }
                }
                if (utterances.isEmpty()) {
                    return@withContext TranscriptionResult.Error(TranscriptionFailure.NoSpeechDetected)
                }

                val text =
                    if (offlineRecognizerProvider.ensureInitialized()) {
                        utterances.mapNotNull { offlineRecognizerProvider.transcribe(it)?.text?.trim() }
                    } else {
                        recognizerProvider.ensureInitialized()
                        utterances.mapNotNull(::transcribeStreamingUtterance)
                    }.filter(String::isNotBlank)
                        .joinToString(" ")

                if (text.isBlank()) {
                    TranscriptionResult.Error(TranscriptionFailure.NoSpeechDetected)
                } else {
                    TranscriptionResult.Success(text = text, isFinal = true)
                }
            } catch (e: Exception) {
                Napier.e("On-device file transcription failed", e)
                TranscriptionResult.Error(TranscriptionFailure.Unknown)
            } finally {
                fileVad.release()
            }
        }

    override suspend fun cancelTranscription() {
        isListening = false
        cancelJobs()
        clearRefinementBuffer()
        stopAudioRecord()
        vadProvider.reset()
        releaseStream()
        terminalizer.cancel()
    }

    override fun getSupportedLanguages(): List<String> = listOf("en-US")

    override fun setLanguage(languageCode: String) {
        Napier.d("Sherpa-ONNX language set request: $languageCode (only en-US supported)")
    }

    override val supportsLiveTranscription: Boolean = true

    override val supportsFileTranscription: Boolean = true

    override val isOfflineModelAvailable: Boolean
        get() = offlineRecognizerProvider.isAvailable

    private val modelManager by lazy { SherpaOnnxModelManager(context) }

    private val _offlineModelDownloadStatus = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)

    override val offlineModelDownloadStatus: StateFlow<ModelDownloadStatus> = _offlineModelDownloadStatus.asStateFlow()

    private var offlineDownloadJob: Job? = null

    override fun startOfflineModelDownload() {
        if (offlineDownloadJob?.isActive == true) return
        if (isOfflineModelAvailable) {
            _offlineModelDownloadStatus.value = ModelDownloadStatus.Completed
            return
        }
        offlineDownloadJob =
            scope.launch(Dispatchers.IO) {
                try {
                    modelManager.downloadWhisperModel().collect { status ->
                        _offlineModelDownloadStatus.value = status
                    }
                } catch (e: Exception) {
                    Napier.e("Whisper download crashed", e)
                    _offlineModelDownloadStatus.value = ModelDownloadStatus.UnknownError
                }
            }
    }

    override suspend fun resetTranscription() {
        accumulator.reset()
        totalAcceptedSamples = 0L
        currentStreamStartMs = 0L
        currentStreamAcceptedSamples = 0L

        if (isListening) {
            stopLiveTranscription()
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
            ar.release()
            throw AudioCaptureException("AudioRecord failed to initialize")
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
                terminalizer.progress(
                    TranscriptionResult.Success(
                        text = accumulator.build(),
                        timedTranscript = accumulator.buildTimedTranscript(),
                        isFinal = false,
                    ),
                )
            }
            currentStreamStartMs = samplesToMs(totalAcceptedSamples)
            currentStreamAcceptedSamples = 0L
            recognizerProvider.reset(s)
        } else if (result.text.isNotBlank()) {
            accumulator.setPartial(result.text)
            terminalizer.progress(
                TranscriptionResult.Success(
                    text = accumulator.build(),
                    timedTranscript = accumulator.buildTimedTranscript(),
                    isFinal = false,
                ),
            )
        }
    }

    private fun transcribeStreamingUtterance(samples: FloatArray): String? {
        if (samples.isEmpty()) return null
        val localStream = recognizerProvider.createStream()
        return try {
            localStream.acceptWaveform(samples, SherpaOnnxRecognizerProvider.SAMPLE_RATE)
            localStream.inputFinished()
            while (recognizerProvider.isReady(localStream)) {
                recognizerProvider.decode(localStream)
            }
            recognizerProvider
                .getResult(localStream)
                .text
                .takeIf(String::isNotBlank)
                ?.let(recognizerProvider::addPunctuation)
        } finally {
            localStream.release()
        }
    }

    private fun FloatArray.asSequenceChunks(chunkSize: Int): Sequence<FloatArray> =
        sequence {
            var offset = 0
            while (offset < size) {
                val end = (offset + chunkSize).coerceAtMost(size)
                yield(copyOfRange(offset, end))
                offset = end
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
        private const val MAX_CONSECUTIVE_EMPTY_READS = 50
        private const val EMPTY_READ_RETRY_DELAY_MS = 10L
        private const val MODEL_INITIALIZATION_TIMEOUT_MS = 30_000L
        private const val REFINEMENT_TIMEOUT_MS = 5 * 60_000L

        /**
         * Maximum samples retained in memory for the refinement pass.
         * 15 minutes at 16kHz mono = ~57 MB of float data. Beyond this, the
         * buffer is dropped and the streaming text becomes the final result.
         */
        private const val MAX_BUFFERED_SAMPLES = 15L * 60 * SherpaOnnxRecognizerProvider.SAMPLE_RATE
    }
}

private class AudioCaptureException(
    message: String,
) : IllegalStateException(message)
