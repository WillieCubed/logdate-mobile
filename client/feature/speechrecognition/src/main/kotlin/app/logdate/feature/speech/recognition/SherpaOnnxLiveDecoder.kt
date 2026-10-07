package app.logdate.feature.speech.recognition

import app.logdate.client.media.audio.transcription.TimedTranscriptBuilder
import app.logdate.client.media.audio.transcription.TimedUtterance
import app.logdate.client.media.audio.transcription.TranscriptAccumulator
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionSessionTerminalizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerResult
import com.k2fsa.sherpa.onnx.OnlineStream
import io.github.aakira.napier.Napier

internal class SherpaOnnxLiveDecoder(
    private val recognizerProvider: SherpaOnnxRecognizerProvider,
    private val vadProvider: SherpaOnnxVadProvider,
    private val accumulator: TranscriptAccumulator,
    private val terminalizer: TranscriptionSessionTerminalizer,
) {
    private var totalAcceptedSamples: Long = 0L
    private var currentStreamStartMs: Long = 0L
    private var currentStreamAcceptedSamples: Long = 0L
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

    val hasRefinementUtterances: Boolean
        get() = !bufferOverflowed && utterancePcmBuffer.isNotEmpty()

    fun resetTiming() {
        totalAcceptedSamples = 0L
        currentStreamStartMs = 0L
        currentStreamAcceptedSamples = 0L
    }

    fun advanceStreamTiming() {
        currentStreamStartMs = samplesToMs(totalAcceptedSamples)
        currentStreamAcceptedSamples = 0L
    }

    fun takeRefinementUtterances(): List<FloatArray> {
        // Transfer ownership without copying or doubling the retained PCM memory.
        val utterances = utterancePcmBuffer
        utterancePcmBuffer = ArrayList()
        bufferedSampleCount = 0
        return utterances
    }

    fun clearRefinementBuffer() {
        utterancePcmBuffer.clear()
        bufferedSampleCount = 0
        bufferOverflowed = false
    }

    /**
     * Routes raw PCM samples through the VAD, then forwards detected speech
     * segments to the recognizer. Silence is dropped before reaching the
     * recognizer, eliminating hallucinated tokens during pauses.
     */
    suspend fun processSamples(
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
    fun bufferUtteranceForRefinement(samples: FloatArray) {
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

    fun acceptWaveform(
        stream: OnlineStream,
        samples: FloatArray,
    ) {
        if (samples.isEmpty()) return
        stream.acceptWaveform(samples, SherpaOnnxRecognizerProvider.SAMPLE_RATE)
        totalAcceptedSamples += samples.size.toLong()
        currentStreamAcceptedSamples += samples.size.toLong()
    }

    fun buildTimedUtterance(
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

    private fun samplesToMs(sampleCount: Long): Long = ((sampleCount * 1000L) / SherpaOnnxRecognizerProvider.SAMPLE_RATE).coerceAtLeast(0L)

    private companion object {
        /**
         * Maximum samples retained in memory for the refinement pass.
         * 15 minutes at 16kHz mono = ~57 MB of float data. Beyond this, the
         * buffer is dropped and the streaming text becomes the final result.
         */
        private const val MAX_BUFFERED_SAMPLES = 15L * 60 * SherpaOnnxRecognizerProvider.SAMPLE_RATE
    }
}
