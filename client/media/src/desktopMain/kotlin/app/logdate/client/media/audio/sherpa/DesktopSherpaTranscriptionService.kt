package app.logdate.client.media.audio.sherpa

import app.logdate.client.media.audio.download.ModelDownloadStatus
import app.logdate.client.media.audio.transcription.TranscriptAccumulator
import app.logdate.client.media.audio.transcription.TranscriptionFailure
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Desktop transcription service backed by Sherpa-ONNX JVM. Unlike Android,
 * desktop runs *post-recording* refinement only — no live streaming pass.
 * Desktop users typically dictate longer-form things and can wait the few
 * extra seconds for a polished result, so the implementation is much
 * smaller than the streaming Zipformer + Whisper two-pass on mobile.
 *
 * Flow:
 *   1. The user records normally via `DesktopAudioRecordingManager` (writes WAV).
 *   2. After stop, `transcribeAudioFile` reads the WAV, segments it through
 *      the VAD, and runs each segment through Whisper.
 *   3. Refined results stream out of [getTranscriptionFlow] one segment at
 *      a time, so the UI can show the transcript filling in.
 *
 * Live transcription methods are no-ops because there's no streaming pass
 * on desktop yet — the service reports `supportsLiveTranscription = false`.
 */
internal class DesktopSherpaTranscriptionService(
    private val modelManager: DesktopSherpaModelManager = DesktopSherpaModelManager(),
    private val vadProvider: DesktopSherpaVadProvider = DesktopSherpaVadProvider(modelManager),
    private val offlineRecognizer: DesktopSherpaOfflineRecognizerProvider =
        DesktopSherpaOfflineRecognizerProvider(modelManager),
    private val wavDecoder: DesktopWavDecoder = DesktopWavDecoder(),
) : TranscriptionService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _transcriptionFlow = MutableSharedFlow<TranscriptionResult>(replay = 1)

    private val _offlineModelDownloadStatus = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)

    override val offlineModelDownloadStatus: StateFlow<ModelDownloadStatus> = _offlineModelDownloadStatus.asStateFlow()

    private var downloadJob: Job? = null

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = _transcriptionFlow.asSharedFlow()

    override suspend fun warmUp() {
        offlineRecognizer.ensureInitialized()
        vadProvider.ensureInitialized()
    }

    override val isOfflineModelAvailable: Boolean
        get() = offlineRecognizer.isAvailable

    override fun startOfflineModelDownload() {
        if (downloadJob?.isActive == true) return
        if (isOfflineModelAvailable) {
            _offlineModelDownloadStatus.value = ModelDownloadStatus.Completed
            return
        }
        downloadJob =
            scope.launch(Dispatchers.IO) {
                try {
                    modelManager.downloadWhisperModel().collect { status ->
                        _offlineModelDownloadStatus.value = status
                    }
                } catch (e: Exception) {
                    Napier.e("Desktop Whisper download crashed", e)
                    _offlineModelDownloadStatus.value = ModelDownloadStatus.UnknownError
                }
            }
    }

    // Desktop has no live streaming pass yet — the recording manager writes
    // a WAV file straight to disk and we transcribe after stop. The Android
    // streaming Zipformer would also work in principle, but skipping it
    // keeps the desktop dependency footprint small and lets refinement run
    // entirely against post-recording audio.
    override val supportsLiveTranscription: Boolean = false

    override val supportsFileTranscription: Boolean = true

    override suspend fun startLiveTranscription(): Boolean = false

    override suspend fun stopLiveTranscription() = Unit

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult {
        if (!offlineRecognizer.ensureInitialized()) {
            return TranscriptionResult.Error(TranscriptionFailure.NotAvailable)
        }
        if (!vadProvider.ensureInitialized()) {
            return TranscriptionResult.Error(TranscriptionFailure.NotAvailable)
        }

        val samples = wavDecoder.decodeToMono16kHz(audioUri)
        if (samples == null || samples.isEmpty()) {
            return TranscriptionResult.Error(TranscriptionFailure.AudioError)
        }

        val accumulator = TranscriptAccumulator()
        try {
            // Push the entire recording through the VAD in one shot, then
            // drain its segment queue and Whisper-decode each speech run.
            vadProvider.acceptWaveform(samples)
            vadProvider.flush()

            while (!vadProvider.isEmpty()) {
                val segment = vadProvider.front()
                vadProvider.pop()

                val result = offlineRecognizer.transcribe(segment.samples) ?: continue
                if (result.text.isBlank()) continue

                accumulator.addSegment(result.text)
                _transcriptionFlow.emit(
                    TranscriptionResult.Success(
                        text = accumulator.build(),
                        timedTranscript = accumulator.buildTimedTranscript(),
                        isFinal = false,
                        isRefining = true,
                    ),
                )
            }
        } finally {
            vadProvider.reset()
        }

        val finalResult =
            TranscriptionResult.Success(
                text = accumulator.build(),
                timedTranscript = accumulator.buildTimedTranscript(),
                isFinal = true,
                isRefining = false,
            )
        _transcriptionFlow.emit(finalResult)
        return finalResult
    }

    override fun cancelTranscription() = Unit

    override fun getSupportedLanguages(): List<String> = listOf("en-US")

    override fun setLanguage(languageCode: String) {
        Napier.d("Desktop Sherpa transcription language set request: $languageCode (only en-US supported)")
    }

    override suspend fun resetTranscription() = Unit

    override fun release() {
        downloadJob?.cancel()
        downloadJob = null
        offlineRecognizer.release()
        vadProvider.release()
    }
}
